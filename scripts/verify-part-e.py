#!/usr/bin/env python3
"""Verify the REST agent against real banking MCP servers, with a stub or live LLM."""

import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import threading
import uuid


spec = importlib.util.spec_from_file_location("part_d", Path(__file__).with_name("verify-part-d.py"))
stack = importlib.util.module_from_spec(spec)
spec.loader.exec_module(stack)


class ModelStub(BaseHTTPRequestHandler):
    """Deterministic tool decisions; all banking work is performed by the real MCP servers."""
    calls = 0

    def log_message(self, *_):
        pass

    def do_POST(self):
        ModelStub.calls += 1
        request = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        messages = request["messages"]
        user = next(message["content"] for message in reversed(messages) if message["role"] == "user")
        if user == "SMOKE provider-error":
            self.respond(503, {"error": {"message": "private-provider-detail", "type": "server_error"}})
            return
        message = {"role": "assistant", "content": "Bonjour !"}
        reason = "stop"
        if messages[-1]["role"] == "tool":
            message["content"] = "MCP result: " + messages[-1]["content"]
        elif user == "SMOKE recall":
            message["content"] = " ".join(str(m["content"]) for m in messages if m["role"] == "user")
        elif user.startswith("SMOKE tool "):
            _, _, tool, arguments = user.split(" ", 3)
            name = next(item["function"]["name"] for item in request["tools"]
                        if item["function"]["name"].endswith(tool))
            message = {"role": "assistant", "content": None, "tool_calls": [{
                "id": "call_" + uuid.uuid4().hex, "type": "function",
                "function": {"name": name, "arguments": arguments}}]}
            reason = "tool_calls"
        self.respond(200, {"id": "chatcmpl-smoke", "object": "chat.completion", "created": 1,
                           "model": "smoke-model", "choices": [{"index": 0, "message": message,
                           "finish_reason": reason}], "usage": {
                               "prompt_tokens": 10, "completion_tokens": 10, "total_tokens": 20}})

    def respond(self, status, body):
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port-offset", type=int, default=10000)
    parser.add_argument("--live-llm", action="store_true", help="Use OPENAI_API_KEY instead of the local model stub")
    args = parser.parse_args()
    if not 0 <= args.port_offset <= 65535 - 8888:
        parser.error("port offset is outside the valid range")
    if args.live_llm and not os.getenv("OPENAI_API_KEY"):
        parser.error("--live-llm requires OPENAI_API_KEY")
    stack.PORT_OFFSET = args.port_offset
    services = {**stack.SERVICES, "ai-agent-service": 8083}
    for name, port in services.items():
        if not (stack.ROOT / name / "target" / f"{name}-0.0.1-SNAPSHOT.jar").is_file():
            parser.error(f"Build {name} with mvn package first")
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", port + args.port_offset))

    logs = Path(tempfile.mkdtemp(prefix="adria-part-e-"))
    print(f"Logs: {logs}; LLM: {'live' if args.live_llm else 'deterministic local stub'}", flush=True)
    config = logs / "config-repo"
    shutil.copytree(stack.ROOT / "config-repo", config)
    env = {**os.environ, "CONFIG_REPO_PATH": config.as_uri(),
           "CONFIG_SERVER_URL": f"http://localhost:{8888 + args.port_offset}",
           "EUREKA_URL": f"http://localhost:{8761 + args.port_offset}/eureka",
           "ACCOUNT_MCP_URL": f"http://localhost:{8081 + args.port_offset}",
           "TRANSACTION_MCP_URL": f"http://localhost:{8082 + args.port_offset}"}
    stub = None
    processes = {}
    try:
        if not args.live_llm:
            stub = ThreadingHTTPServer(("127.0.0.1", 0), ModelStub)
            threading.Thread(target=stub.serve_forever, daemon=True).start()
            env.update(OPENAI_API_KEY="test-key", OPENAI_MODEL="smoke-model",
                       OPENAI_BASE_URL=f"http://localhost:{stub.server_port}/v1")
        for name, port in services.items():
            jar = stack.ROOT / name / "target" / f"{name}-0.0.1-SNAPSHOT.jar"
            with (logs / f"{name}.log").open("w") as output:
                processes[name] = subprocess.Popen([
                    "java", "-Xms64m", "-Xmx256m", "-jar", str(jar),
                    f"--server.port={port + args.port_offset}"],
                    cwd=stack.ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
            stack.eventually(lambda: stack.request(port, "/actuator/health")[0] == 200, processes)
            print(f"OK started {name}", flush=True)

        stack.eventually(lambda: stack.request(8080, "/api/agent/tools")[0] == 200, processes)
        tools = stack.request(8080, "/api/agent/tools")[1]
        assert len(tools) == 12, tools
        assert not any(tool["name"].endswith(("debitAccount", "creditAccount")) for tool in tools)
        print("OK 12 MCP tools discovered; direct debit/credit excluded", flush=True)

        def chat(tool, params, natural, conversation=None):
            text = natural if args.live_llm else f"SMOKE tool {tool} {json.dumps(params)}"
            body = {"message": text}
            if conversation:
                body["conversationId"] = conversation
            status, response, _ = stack.request(8080, "/api/agent/chat", body, timeout=55)
            assert status == 200 and response.get("answer"), (status, response)
            return response

        chat("listAccounts", {}, "Liste les comptes bancaires avec leur référence client et leur solde.")
        print("OK REST chat -> model -> account MCP -> model answer", flush=True)
        accounts = []
        for reference, balance in (("AGENT-SOURCE", 1000), ("AGENT-TARGET", 0)):
            status, account, _ = stack.request(8080, "/api/accounts", {
                "customerReference": reference, "currency": "EUR", "initialBalance": balance})
            assert status == 201, account
            accounts.append(account["id"])
        payment = {"accountId": accounts[0], "amount": 250, "currency": "EUR",
                   "merchantCategory": "GROCERY", "country": "FR"}
        chat("createPayment", payment, f"Exécute maintenant un paiement de 250 EUR sur le compte {accounts[0]}, "
             "catégorie GROCERY, pays FR. Toutes les informations sont fournies.")
        transfer = {"sourceAccountId": accounts[0], "targetAccountId": accounts[1],
                    "amount": 100, "currency": "EUR", "country": "FR"}
        chat("transfer", transfer, f"Exécute maintenant un virement de 100 EUR du compte {accounts[0]} "
             f"vers le compte {accounts[1]}, pays FR. Toutes les informations sont fournies.")
        for account, balance in zip(accounts, (650, 100)):
            assert stack.request(8080, f"/api/accounts/{account}")[1]["balance"] == balance
        transactions = stack.request(8080, "/api/transactions")[1]
        assert len(transactions) == 3, transactions
        print("OK agent payment and transfer: 3 ledger records, balances 650/100", flush=True)
        chat("getMaxTransactionAmount", {}, "Quel est le plafond actuel pour une transaction ?")
        print("OK agent reads the configured transaction limit through MCP", flush=True)

        if not args.live_llm:
            rejected = {**payment, "amount": 1001}
            result = chat("createPayment", rejected, "")
            assert "exceed" in result["answer"].lower(), result
            assert len(stack.request(8080, "/api/transactions")[1]) == 3
            assert stack.request(8080, f"/api/accounts/{accounts[0]}")[1]["balance"] == 650
            print("OK MCP business-rule rejection leaves ledger and balance unchanged", flush=True)
            conversation = str(uuid.uuid4())
            for message in ("SMOKE remember CUST-MEMORY", "SMOKE recall"):
                status, response, _ = stack.request(8080, "/api/agent/chat", {
                    "message": message, "conversationId": conversation})
                assert status == 200, response
            assert "CUST-MEMORY" in response["answer"]
            assert stack.request(8080, f"/api/agent/conversations/{conversation}", method="DELETE")[0] == 204
            status, response, _ = stack.request(8080, "/api/agent/chat", {
                "message": "SMOKE recall", "conversationId": conversation})
            assert status == 200 and "CUST-MEMORY" not in response["answer"]
            before = ModelStub.calls
            status, response, _ = stack.request(8080, "/api/agent/chat", {"message": "SMOKE provider-error"})
            assert status == 503 and "private-provider-detail" not in json.dumps(response)
            assert ModelStub.calls == before + 1
            print("OK memory, deletion, sanitized provider errors and no HTTP retries", flush=True)
        print("Partie E: all live-stack checks passed.", flush=True)
    finally:
        # Stop clients before the registry, without touching pre-existing local services.
        for process in reversed(list(processes.values())):
            process.terminate()
            try:
                process.wait(timeout=30)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        if stub:
            stub.shutdown()
            stub.server_close()


if __name__ == "__main__":
    main()
