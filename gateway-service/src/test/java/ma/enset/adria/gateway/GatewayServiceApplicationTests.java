package ma.enset.adria.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false"
})
class GatewayServiceApplicationTests {

    private static final HttpServer DOWNSTREAM = downstream();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void discovery(DynamicPropertyRegistry registry) {
        // Exercise the real lb:// routes without depending on a running Eureka server.
        for (String service : new String[]{"account-service", "transaction-service"}) {
            registry.add("spring.cloud.discovery.client.simple.instances." + service + "[0].uri",
                    () -> "http://localhost:" + DOWNSTREAM.getAddress().getPort());
        }
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @Test
    void restRoutesPreservePathsAndQueryParameters() throws Exception {
        for (String path : new String[]{"/api/accounts?customerReference=CUST-001", "/api/transactions/limits"}) {
            HttpResponse<String> response = request("GET", path, null);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("GET " + path);
        }
    }

    @Test
    void graphqlRoutesStripServicePrefixAndForwardBody() throws Exception {
        for (String service : new String[]{"account-service", "transaction-service"}) {
            HttpResponse<String> response = request("POST", "/" + service + "/graphql", "{\"query\":\"{ __typename }\"}");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("POST /graphql {\"query\":\"{ __typename }\"}");
        }
    }

    @Test
    void mcpRoutesForwardSessionHeadersAndBody() throws Exception {
        for (String service : new String[]{"account-service", "transaction-service"}) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/" + service + "/mcp"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Mcp-Session-Id", "test-session")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\"}")).build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Mcp-Session-Id")).contains("test-session");
            assertThat(response.body()).isEqualTo("POST /mcp {\"jsonrpc\":\"2.0\"}");
        }
    }

    @Test
    void businessErrorsArePreserved() throws Exception {
        HttpResponse<String> response = request("GET", "/api/accounts/missing", null);
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).isEqualTo("Account not found");
    }

    @Test
    void unavailableServiceReturnsStructured503() throws Exception {
        // No instance is registered for the future AI agent.
        HttpResponse<String> response = request("POST", "/api/agent/chat", "{\"message\":\"hello\"}");
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        assertThat(response.body()).contains("Service unavailable", "ai-agent-service", "503");
    }

    @Test
    void corsPreflightAllowsFrontendAndMcpHeaders() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/account-service/mcp"))
                .timeout(Duration.ofSeconds(10))
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,mcp-session-id,mcp-protocol-version")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:4200");
        assertThat(response.headers().firstValue("Access-Control-Expose-Headers")).contains("Mcp-Session-Id");
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private static HttpServer downstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String session = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
                if (session != null) {
                    exchange.getResponseHeaders().set("Mcp-Session-Id", session);
                }
                boolean missing = exchange.getRequestURI().getPath().endsWith("/missing");
                String result = missing ? "Account not found" : exchange.getRequestMethod() + " "
                        + exchange.getRequestURI() + (body.isEmpty() ? "" : " " + body);
                byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(missing ? 404 : 200, bytes.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

}
