package ma.enset.adria.transaction.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/**
 * Resolved through Eureka by default; set account-service.url to call a fixed address instead.
 */
@FeignClient(name = "account-service", url = "${account-service.url:}", configuration = AccountClientConfig.class)
public interface AccountClient {

    @GetMapping("/api/accounts/{id}")
    AccountDto getAccount(@PathVariable UUID id);

    @PostMapping("/api/accounts/{id}/debit")
    AccountDto debit(@PathVariable UUID id, @RequestBody AmountDto amount);

    @PostMapping("/api/accounts/{id}/credit")
    AccountDto credit(@PathVariable UUID id, @RequestBody AmountDto amount);
}
