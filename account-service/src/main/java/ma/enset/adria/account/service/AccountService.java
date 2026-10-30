package ma.enset.adria.account.service;

import jakarta.validation.Valid;
import ma.enset.adria.account.domain.AccountStatus;
import ma.enset.adria.account.dto.AccountRequest;
import ma.enset.adria.account.dto.AccountResponse;

import java.util.List;
import java.util.UUID;

/**
 * Single business layer shared by the REST, GraphQL and MCP adapters.
 */
public interface AccountService {

    AccountResponse create(@Valid AccountRequest request);

    AccountResponse getById(UUID id);

    List<AccountResponse> getAll();

    List<AccountResponse> getByCustomerReference(String customerReference);

    AccountResponse update(UUID id, @Valid AccountRequest request);

    void delete(UUID id);

    AccountResponse changeStatus(UUID id, AccountStatus status);

    AccountResponse debit(UUID id, double amount);

    AccountResponse credit(UUID id, double amount);
}
