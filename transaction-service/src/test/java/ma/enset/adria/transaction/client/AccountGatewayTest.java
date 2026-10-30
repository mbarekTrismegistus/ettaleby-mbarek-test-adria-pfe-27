package ma.enset.adria.transaction.client;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import ma.enset.adria.transaction.exception.AccountNotActiveException;
import ma.enset.adria.transaction.exception.AccountNotFoundException;
import ma.enset.adria.transaction.exception.AccountServiceUnavailableException;
import ma.enset.adria.transaction.exception.BusinessRuleException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises OpenFeign + Resilience4J against a fake account-service (JDK HttpServer).
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false",
        "resilience4j.retry.instances.accountService.wait-duration=10ms"
})
class AccountGatewayTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String ACCOUNT_JSON =
            "{\"id\":\"" + ID + "\",\"customerReference\":\"CUST-001\",\"status\":\"ACTIVE\",\"currency\":\"EUR\",\"balance\":5000.0}";

    private static final HttpServer server;
    private static final AtomicInteger hits = new AtomicInteger();
    private static volatile int status = 200;
    private static volatile String body = ACCOUNT_JSON;

    static {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/api/accounts", exchange -> {
            hits.incrementAndGet();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @DynamicPropertySource
    static void accountServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("account-service.url", () -> "http://localhost:" + server.getAddress().getPort());
    }

    @Autowired
    private AccountGateway accountGateway;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void reset() {
        hits.set(0);
        respond(200, ACCOUNT_JSON);
        circuitBreakerRegistry.circuitBreaker("accountService").reset();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    private static void respond(int newStatus, String newBody) {
        status = newStatus;
        body = newBody;
    }

    private static String problem(int code, String detail) {
        return "{\"status\":" + code + ",\"detail\":\"" + detail + "\"}";
    }

    @Test
    void getAccountReturnsAccount() {
        AccountDto account = accountGateway.getAccount(ID);
        assertThat(account.currency()).isEqualTo("EUR");
        assertThat(account.balance()).isEqualTo(5000.0);
        assertThat(account.isActive()).isTrue();
    }

    @Test
    void debitSendsAmount() {
        assertThat(accountGateway.debit(ID, new BigDecimal("100.00")).id()).isEqualTo(ID);
        assertThat(hits).hasValue(1);
    }

    @Test
    void notFoundIsBusinessErrorAndNotRetried() {
        respond(404, problem(404, "Account not found: " + ID));
        assertThatThrownBy(() -> accountGateway.getAccount(ID))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("Account not found");
        assertThat(hits).hasValue(1);
    }

    @Test
    void blockedAccountIsBusinessError() {
        respond(409, problem(409, "Account is not active: " + ID));
        assertThatThrownBy(() -> accountGateway.debit(ID, BigDecimal.TEN))
                .isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    void insufficientBalanceIsBusinessError() {
        respond(422, problem(422, "Insufficient balance"));
        assertThatThrownBy(() -> accountGateway.debit(ID, BigDecimal.TEN))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Insufficient balance");
    }

    @Test
    void technicalFailureOnReadIsRetriedThenFallsBack() {
        respond(500, "boom");
        assertThatThrownBy(() -> accountGateway.getAccount(ID))
                .isInstanceOf(AccountServiceUnavailableException.class);
        assertThat(hits).hasValue(3);
    }

    @Test
    void technicalFailureOnDebitIsNotRetried() {
        respond(500, "boom");
        assertThatThrownBy(() -> accountGateway.debit(ID, BigDecimal.TEN))
                .isInstanceOf(AccountServiceUnavailableException.class);
        assertThat(hits).hasValue(1);
    }

    @Test
    void circuitOpensAfterRepeatedFailuresAndStopsCallingAccountService() {
        respond(500, "boom");
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> accountGateway.debit(ID, BigDecimal.TEN))
                    .isInstanceOf(AccountServiceUnavailableException.class);
        }
        assertThat(circuitBreakerRegistry.circuitBreaker("accountService").getState().name()).isEqualTo("OPEN");

        int hitsWhenOpened = hits.get();
        respond(200, ACCOUNT_JSON);
        assertThatThrownBy(() -> accountGateway.debit(ID, BigDecimal.TEN))
                .isInstanceOf(AccountServiceUnavailableException.class);
        assertThat(hits).hasValue(hitsWhenOpened);
    }

    @Test
    void businessErrorsDoNotOpenTheCircuit() {
        respond(404, problem(404, "Account not found"));
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> accountGateway.getAccount(ID)).isInstanceOf(AccountNotFoundException.class);
        }
        assertThat(circuitBreakerRegistry.circuitBreaker("accountService").getState().name()).isEqualTo("CLOSED");
    }
}
