package ma.enset.adria.account.config;

import lombok.extern.slf4j.Slf4j;
import ma.enset.adria.account.domain.Account;
import ma.enset.adria.account.domain.AccountStatus;
import ma.enset.adria.account.repository.AccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.UUID;

/**
 * Seeds accounts with fixed ids so tests, screenshots and the AI agent stay reproducible.
 */
@Slf4j
@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedAccounts(AccountRepository accountRepository) {
        return args -> {
            if (accountRepository.count() > 0) {
                return;
            }
            accountRepository.saveAll(List.of(
                    account("11111111-1111-1111-1111-111111111111", "CUST-001", "EUR", 5000, AccountStatus.ACTIVE),
                    account("22222222-2222-2222-2222-222222222222", "CUST-002", "EUR", 1200, AccountStatus.ACTIVE),
                    account("33333333-3333-3333-3333-333333333333", "CUST-003", "MAD", 20000, AccountStatus.ACTIVE),
                    account("44444444-4444-4444-4444-444444444444", "CUST-004", "EUR", 800, AccountStatus.BLOCKED)
            ));
            log.info("Seeded {} accounts", accountRepository.count());
        };
    }

    private Account account(String id, String customerReference, String currency, double balance, AccountStatus status) {
        return Account.builder()
                .id(UUID.fromString(id))
                .customerReference(customerReference)
                .currency(currency)
                .balance(balance)
                .status(status)
                .build();
    }
}
