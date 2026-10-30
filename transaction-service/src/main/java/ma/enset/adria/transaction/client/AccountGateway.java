package ma.enset.adria.transaction.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.enset.adria.transaction.exception.AccountServiceUnavailableException;
import ma.enset.adria.transaction.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fault-tolerant access to account-service.
 * Reads are retried; debit/credit are NOT retried because they are not idempotent
 * (a retry after a timeout could move the money twice).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountGateway {

    private static final String ACCOUNT_SERVICE = "accountService";

    private final AccountClient accountClient;

    @Retry(name = ACCOUNT_SERVICE)
    @CircuitBreaker(name = ACCOUNT_SERVICE, fallbackMethod = "fallback")
    public AccountDto getAccount(UUID accountId) {
        return accountClient.getAccount(accountId);
    }

    @CircuitBreaker(name = ACCOUNT_SERVICE, fallbackMethod = "fallback")
    public AccountDto debit(UUID accountId, BigDecimal amount) {
        return accountClient.debit(accountId, new AmountDto(amount.doubleValue()));
    }

    @CircuitBreaker(name = ACCOUNT_SERVICE, fallbackMethod = "fallback")
    public AccountDto credit(UUID accountId, BigDecimal amount) {
        return accountClient.credit(accountId, new AmountDto(amount.doubleValue()));
    }

    private AccountDto fallback(UUID accountId, Throwable ex) {
        return unavailable(accountId, ex);
    }

    private AccountDto fallback(UUID accountId, BigDecimal amount, Throwable ex) {
        return unavailable(accountId, ex);
    }

    private AccountDto unavailable(UUID accountId, Throwable ex) {
        // business errors (404, 409, 422) are real answers from account-service: propagate them as-is
        if (ex instanceof BusinessException businessException) {
            throw businessException;
        }
        log.warn("account-service unavailable for account {}: {}", accountId, ex.toString());
        throw new AccountServiceUnavailableException(ex);
    }
}
