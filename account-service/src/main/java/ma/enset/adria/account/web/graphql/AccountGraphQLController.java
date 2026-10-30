package ma.enset.adria.account.web.graphql;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.enset.adria.account.domain.AccountStatus;
import ma.enset.adria.account.dto.AccountRequest;
import ma.enset.adria.account.dto.AccountResponse;
import ma.enset.adria.account.service.AccountService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class AccountGraphQLController {

    private final AccountService accountService;

    @QueryMapping
    public List<AccountResponse> accounts() {
        return accountService.getAll();
    }

    @QueryMapping
    public AccountResponse accountById(@Argument UUID id) {
        return accountService.getById(id);
    }

    @QueryMapping
    public List<AccountResponse> accountsByCustomer(@Argument String customerReference) {
        return accountService.getByCustomerReference(customerReference);
    }

    @MutationMapping
    public AccountResponse createAccount(@Argument @Valid AccountRequest input) {
        return accountService.create(input);
    }

    @MutationMapping
    public AccountResponse updateAccount(@Argument UUID id, @Argument @Valid AccountRequest input) {
        return accountService.update(id, input);
    }

    @MutationMapping
    public boolean deleteAccount(@Argument UUID id) {
        accountService.delete(id);
        return true;
    }

    @MutationMapping
    public AccountResponse changeStatus(@Argument UUID id, @Argument AccountStatus status) {
        return accountService.changeStatus(id, status);
    }

    @MutationMapping
    public AccountResponse debit(@Argument UUID id, @Argument double amount) {
        return accountService.debit(id, amount);
    }

    @MutationMapping
    public AccountResponse credit(@Argument UUID id, @Argument double amount) {
        return accountService.credit(id, amount);
    }
}
