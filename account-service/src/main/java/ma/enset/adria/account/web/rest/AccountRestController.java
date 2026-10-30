package ma.enset.adria.account.web.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.enset.adria.account.dto.AccountRequest;
import ma.enset.adria.account.dto.AccountResponse;
import ma.enset.adria.account.dto.AmountRequest;
import ma.enset.adria.account.dto.StatusRequest;
import ma.enset.adria.account.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountRestController {

    private final AccountService accountService;

    @GetMapping
    public List<AccountResponse> getAll(@RequestParam(required = false) String customerReference) {
        return customerReference == null
                ? accountService.getAll()
                : accountService.getByCustomerReference(customerReference);
    }

    @GetMapping("/{id}")
    public AccountResponse getById(@PathVariable UUID id) {
        return accountService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody AccountRequest request) {
        return accountService.create(request);
    }

    @PutMapping("/{id}")
    public AccountResponse update(@PathVariable UUID id, @Valid @RequestBody AccountRequest request) {
        return accountService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        accountService.delete(id);
    }

    @PatchMapping("/{id}/status")
    public AccountResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return accountService.changeStatus(id, request.status());
    }

    @PostMapping("/{id}/debit")
    public AccountResponse debit(@PathVariable UUID id, @Valid @RequestBody AmountRequest request) {
        return accountService.debit(id, request.amount());
    }

    @PostMapping("/{id}/credit")
    public AccountResponse credit(@PathVariable UUID id, @Valid @RequestBody AmountRequest request) {
        return accountService.credit(id, request.amount());
    }
}
