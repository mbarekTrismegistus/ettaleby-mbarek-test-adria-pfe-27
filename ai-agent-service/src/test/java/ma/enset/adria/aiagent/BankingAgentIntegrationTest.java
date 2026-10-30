package ma.enset.adria.aiagent;

import ma.enset.adria.aiagent.dto.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.ai.mcp.client.enabled=false", "spring.ai.model.chat=none",
        "spring.ai.openai.api-key=test-key"
})
@Import(BankingAgentIntegrationTest.Fixtures.class)
class BankingAgentIntegrationTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    @LocalServerPort
    int port;
    @Autowired
    ScriptedModel model;
    @Autowired
    AtomicInteger toolCalls;

    @BeforeEach
    void reset() {
        model.prompts.clear();
        model.responder = prompt -> reply("Bonjour !");
        toolCalls.set(0);
    }

    @Test
    void createsConversationAndReturnsAnswer() throws Exception {
        var response = chat(Map.of("message", "Bonjour"));
        assertThat(response.statusCode()).isEqualTo(200);
        ChatResponse result = JSON.readValue(response.body(), ChatResponse.class);
        assertThat(result.conversationId()).isNotNull();
        assertThat(result.answer()).isEqualTo("Bonjour !");
        assertThat(model.prompts.getFirst().getSystemMessage().getText()).contains("MCP tools", "createPayment");
    }

    @Test
    void executesToolAndSendsResultBackToModel() throws Exception {
        model.responder = prompt -> {
            if (prompt.getInstructions().getLast() instanceof ToolResponseMessage result) {
                return reply("Solde : " + result.getResponses().getFirst().responseData());
            }
            var call = new AssistantMessage.ToolCall("call-1", "function", "getBalance", "{}");
            return new org.springframework.ai.chat.model.ChatResponse(List.of(new Generation(
                    AssistantMessage.builder().content("").toolCalls(List.of(call)).build())));
        };
        var response = chat(Map.of("message", "Quel est mon solde ?"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readValue(response.body(), ChatResponse.class).answer()).contains("750");
        assertThat(toolCalls.get()).isEqualTo(1);
        assertThat(model.prompts).hasSize(2);
    }

    @Test
    void retainsHistoryOnlyForTheRequestedConversation() throws Exception {
        UUID id = UUID.randomUUID();
        chat(Map.of("message", "Ma référence est CUST-001", "conversationId", id));
        chat(Map.of("message", "Et mon solde ?", "conversationId", id));
        assertThat(model.prompts.getLast().getInstructions())
                .anySatisfy(message -> assertThat(message.getText()).contains("CUST-001"));
        chat(Map.of("message", "Autre conversation", "conversationId", UUID.randomUUID()));
        assertThat(model.prompts.getLast().getInstructions())
                .noneSatisfy(message -> assertThat(message.getText()).contains("CUST-001"));
    }

    @Test
    void clearsConversationHistory() throws Exception {
        UUID id = UUID.randomUUID();
        chat(Map.of("message", "Ma référence est CUST-SECRET", "conversationId", id));
        assertThat(send("DELETE", "/api/agent/conversations/" + id, null).statusCode()).isEqualTo(204);
        chat(Map.of("message", "Bonjour", "conversationId", id));
        assertThat(model.prompts.getLast().getInstructions())
                .noneSatisfy(message -> assertThat(message.getText()).contains("CUST-SECRET"));
    }

    @Test
    void rejectsInvalidRequestsBeforeCallingModel() throws Exception {
        for (Map<String, ?> request : List.<Map<String, ?>>of(Map.of(), Map.of("message", "  "),
                Map.of("message", "x".repeat(4001)), Map.of("message", "hello", "conversationId", "invalid"))) {
            assertThat(chat(request).statusCode()).isEqualTo(400);
        }
        assertThat(model.prompts).isEmpty();
    }

    @Test
    void listsToolsWithoutCallingTheModelOrExecutingThem() throws Exception {
        var response = send("GET", "/api/agent/tools", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("getBalance", "Test balance lookup");
        assertThat(model.prompts).isEmpty();
        assertThat(toolCalls.get()).isZero();
    }

    @Test
    void reportsProviderFailureWithoutRetryingOrLeakingDetails() throws Exception {
        model.responder = prompt -> { throw new IllegalStateException("secret-provider-key"); };
        UUID id = UUID.randomUUID();
        var response = chat(Map.of("message", "Bonjour", "conversationId", id));
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("Banking assistant unavailable", id.toString(), "check transaction history")
                .doesNotContain("secret-provider-key");
        assertThat(model.prompts).hasSize(1);
    }

    @Test
    void reportsEmptyModelReplyAsUnavailable() throws Exception {
        model.responder = prompt -> reply("");
        assertThat(chat(Map.of("message", "Bonjour")).statusCode()).isEqualTo(503);
    }

    private HttpResponse<String> chat(Map<String, ?> body) throws Exception {
        return send("POST", "/api/agent/chat", body);
    }

    private HttpResponse<String> send(String method, String path, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static org.springframework.ai.chat.model.ChatResponse reply(String text) {
        return new org.springframework.ai.chat.model.ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    static class ScriptedModel implements ChatModel {
        final List<Prompt> prompts = new CopyOnWriteArrayList<>();
        Function<Prompt, org.springframework.ai.chat.model.ChatResponse> responder;

        @Override
        public org.springframework.ai.model.tool.ToolCallingChatOptions getOptions() {
            return org.springframework.ai.model.tool.ToolCallingChatOptions.builder().build();
        }

        @Override
        public org.springframework.ai.chat.model.ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            return responder.apply(prompt);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixtures {
        @Bean
        ScriptedModel chatModel() {
            return new ScriptedModel();
        }

        @Bean
        AtomicInteger toolCalls() {
            return new AtomicInteger();
        }

        @Bean
        ToolCallbackProvider tools(AtomicInteger calls) {
            return ToolCallbackProvider.from(FunctionToolCallback.builder("getBalance", () -> {
                calls.incrementAndGet();
                return Map.of("balance", 750, "currency", "EUR");
            }).description("Test balance lookup").build());
        }
    }
}
