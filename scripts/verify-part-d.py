#!/usr/bin/env python3
"""Start the five services, verify partie D, then stop only the processes started here."""

import argparse
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request


ROOT = Path(__file__).resolve().parents[1]
SERVICES = {"discovery-service": 8761, "config-service": 8888,
            "account-service": 8081, "transaction-service": 8082, "gateway-service": 8080}
HTTP = urllib.request.build_opener(urllib.request.ProxyHandler({}))
PORT_OFFSET = 0


def request(port, path, body=None, method=None, headers=None, timeout=8):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f"http://localhost:{port + PORT_OFFSET}{path}", data=data,
                                 method=method, headers={"Content-Type": "application/json", **(headers or {})})
    try:
        response = HTTP.open(req, timeout=timeout)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read().decode()
        if raw.lstrip().startswith(("{", "[")):
            result = json.loads(raw)
        elif "text/event-stream" in response.headers.get("Content-Type", ""):
            result = next(json.loads(line[5:].strip()) for line in raw.splitlines() if line.startswith("data:"))
        else:
            result = raw
        return response.status, result, response.headers


def eventually(check, processes, timeout=120):
    deadline = time.monotonic() + timeout
    last_error = None
    while time.monotonic() < deadline:
        for name, process in processes.items():
            if process.poll() is not None:
                raise RuntimeError(f"{name} exited with {process.returncode}")
        try:
            if check():
                return
        except (OSError, ValueError, StopIteration) as error:
            last_error = error
        time.sleep(1)
    raise TimeoutError(f"Check did not succeed within {timeout}s: {last_error}")


def check_mcp(service):
    headers = {"Accept": "application/json, text/event-stream"}
    status, result, response_headers = request(8080, f"/{service}/mcp", {
        "jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {
            "protocolVersion": "2025-03-26", "capabilities": {},
            "clientInfo": {"name": "part-d-smoke", "version": "1.0"}}}, headers=headers)
    assert status == 200 and "result" in result, (status, result)
    headers["MCP-Protocol-Version"] = result["result"]["protocolVersion"]
    if session := response_headers.get("Mcp-Session-Id"):
        headers["Mcp-Session-Id"] = session
    status, _, _ = request(8080, f"/{service}/mcp", {
        "jsonrpc": "2.0", "method": "notifications/initialized"}, headers=headers)
    assert status in (200, 202, 204), status
    status, result, _ = request(8080, f"/{service}/mcp", {
        "jsonrpc": "2.0", "id": 2, "method": "tools/list"}, headers=headers)
    assert status == 200 and result.get("result", {}).get("tools"), (status, result)
    print(f"OK MCP {service}: {len(result['result']['tools'])} tools", flush=True)
    if session:
        request(8080, f"/{service}/mcp", method="DELETE", headers=headers)


def main():
    global PORT_OFFSET
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port-offset", type=int, default=0,
                        help="Add this offset to every port to avoid existing local services")
    PORT_OFFSET = parser.parse_args().port_offset
    if not 0 <= PORT_OFFSET <= 65535 - max(SERVICES.values()):
        parser.error("port offset is outside the valid range")
    jars = {name: ROOT / name / "target" / f"{name}-0.0.1-SNAPSHOT.jar" for name in SERVICES}
    for name, port in SERVICES.items():
        if not jars[name].is_file():
            raise RuntimeError(f"Missing {jars[name]}; run mvn package first")
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", port + PORT_OFFSET))

    logs = Path(tempfile.mkdtemp(prefix="adria-part-d-"))
    print(f"Logs: {logs}", flush=True)
    config = logs / "config-repo"
    shutil.copytree(ROOT / "config-repo", config)
    env = {**os.environ, "CONFIG_REPO_PATH": config.as_uri(),
           "CONFIG_SERVER_URL": f"http://localhost:{8888 + PORT_OFFSET}",
           "EUREKA_URL": f"http://localhost:{8761 + PORT_OFFSET}/eureka"}
    processes = {}
    try:
        for name, port in SERVICES.items():
            with (logs / f"{name}.log").open("w") as output:
                processes[name] = subprocess.Popen(
                    ["java", "-Xms64m", "-Xmx256m", "-jar", str(jars[name]),
                     f"--server.port={port + PORT_OFFSET}"],
                    cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
            eventually(lambda: request(port, "/actuator/health")[0] == 200, processes)
            print(f"OK started {name}:{port + PORT_OFFSET}", flush=True)

        status, config_response, _ = request(8888, "/transaction-service/default")
        assert status == 200 and any(source["source"].get("bank.transaction.max-amount") == "1000"
                                     for source in config_response["propertySources"])
        print("OK Config Server supplies ceiling 1000", flush=True)

        def registered():
            status, registry, _ = request(8761, "/eureka/apps", headers={"Accept": "application/json"})
            names = {app["name"] for app in registry["applications"].get("application", [])}
            return status == 200 and {name.upper() for name in SERVICES if name != "discovery-service"} <= names

        eventually(registered, processes)
        eventually(lambda: request(8080, "/api/transactions/limits")[1] == {"maxAmount": 1000}, processes)
        print("OK Eureka registration and gateway discovery routing", flush=True)

        accounts = []
        for reference, balance in (("SMOKE-SOURCE", 1000), ("SMOKE-TARGET", 0)):
            status, account, _ = request(8080, "/api/accounts", {
                "customerReference": reference, "currency": "EUR", "initialBalance": balance})
            assert status == 201, account
            accounts.append(account["id"])
        payment = {"accountId": accounts[0], "amount": 1001, "currency": "EUR",
                   "merchantCategory": "SHOPPING", "country": "FR"}
        status, result, _ = request(8080, "/api/transactions", payment)
        assert 400 <= status < 500, (status, result)
        payment["amount"] = 250
        status, result, _ = request(8080, "/api/transactions", payment)
        assert status == 201, result
        status, result, _ = request(8080, "/api/transactions/transfer", {
            "sourceAccountId": accounts[0], "targetAccountId": accounts[1],
            "amount": 100, "currency": "EUR", "country": "FR"})
        assert status == 201, result
        for account_id, balance in zip(accounts, (650, 100)):
            assert request(8080, f"/api/accounts/{account_id}")[1]["balance"] == balance
        print("OK REST payment, ceiling validation, Feign transfer and balances", flush=True)

        for service, query in (("account-service", "{ accounts { id } }"),
                               ("transaction-service", "{ maxTransactionAmount }")):
            status, result, _ = request(8080, f"/{service}/graphql", {"query": query})
            assert status == 200 and "data" in result and "errors" not in result, result
            print(f"OK GraphQL {service}", flush=True)
            check_mcp(service)

        ceiling = config / "transaction-service.properties"
        ceiling.write_text(ceiling.read_text().replace("max-amount=1000", "max-amount=1500"))
        assert request(8082, "/actuator/refresh", method="POST")[0] == 200
        eventually(lambda: request(8080, "/api/transactions/limits")[1] == {"maxAmount": 1500}, processes)
        print("OK ceiling refreshed to 1500 without restarting", flush=True)

        processes["account-service"].terminate()
        processes["account-service"].wait(timeout=30)
        del processes["account-service"]
        status, result, _ = request(8080, "/api/accounts")
        assert status == 503 and result["title"] == "Service unavailable", (status, result)
        print("OK stopped account service returns structured 503", flush=True)
        print("Partie D: all live checks passed.", flush=True)
    finally:
        for process in processes.values():
            process.terminate()
        for process in processes.values():
            try:
                process.wait(timeout=30)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()


if __name__ == "__main__":
    main()
