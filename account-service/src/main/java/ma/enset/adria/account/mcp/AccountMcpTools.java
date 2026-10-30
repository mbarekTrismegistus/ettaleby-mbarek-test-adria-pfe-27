package ma.enset.adria.account.mcp;

import lombok.RequiredArgsConstructor;
import ma.enset.adria.account.domain.AccountStatus;
import ma.enset.adria.account.dto.AccountRequest;
import ma.enset.adria.account.dto.AccountResponse;
import ma.enset.adria.account.service.AccountService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * MCP adapter: exposes the account service as tools for the AI agent.
 */
@Component
@RequiredArgsConstructor
public class AccountMcpTools {

    private final AccountService accountService;

    @McpTool(name = "listAccounts", description = "List all bank accounts with their id, customer reference, status, currency and balance.")
    public List<AccountResponse> listAccounts() {
        return accountService.getAll();
    }

    @McpTool(name = "getAccount", description = "Get a bank account (status, currency, balance) by its UUID.")
    public AccountResponse getAccount(
            @McpToolParam(description = "Account UUID") UUID accountId) {
        return accountService.getById(accountId);
    }

    @McpTool(name = "findAccountsByCustomer", description = "Find the bank accounts owned by a customer, using the customer reference (e.g. CUST-001).")
    public List<AccountResponse> findAccountsByCustomer(
            @McpToolParam(description = "Customer reference, e.g. CUST-001") String customerReference) {
        return accountService.getByCustomerReference(customerReference);
    }

    @McpTool(name = "createAccount", description = "Open a new ACTIVE bank account for a customer.")
    public AccountResponse createAccount(
            @McpToolParam(description = "Customer reference, e.g. CUST-010") String customerReference,
            @McpToolParam(description = "ISO 4217 currency code in upper case, e.g. EUR, MAD, USD") String currency,
            @McpToolParam(description = "Initial balance, zero or positive", required = false) Double initialBalance) {
        return accountService.create(new AccountRequest(customerReference, currency, initialBalance));
    }

    @McpTool(name = "blockAccount", description = "Block a bank account. A blocked account cannot be debited or credited.")
    public AccountResponse blockAccount(
            @McpToolParam(description = "Account UUID") UUID accountId) {
        return accountService.changeStatus(accountId, AccountStatus.BLOCKED);
    }

    @McpTool(name = "activateAccount", description = "Re-activate a blocked bank account.")
    public AccountResponse activateAccount(
            @McpToolParam(description = "Account UUID") UUID accountId) {
        return accountService.changeStatus(accountId, AccountStatus.ACTIVE);
    }

    @McpTool(name = "debitAccount", description = "Withdraw an amount from an ACTIVE account. Fails if the balance is insufficient. "
            + "To record a payment or a transfer, prefer the transaction service tools.")
    public AccountResponse debitAccount(
            @McpToolParam(description = "Account UUID") UUID accountId,
            @McpToolParam(description = "Strictly positive amount, in the account currency") double amount) {
        return accountService.debit(accountId, amount);
    }

    @McpTool(name = "creditAccount", description = "Deposit an amount on an ACTIVE account.")
    public AccountResponse creditAccount(
            @McpToolParam(description = "Account UUID") UUID accountId,
            @McpToolParam(description = "Strictly positive amount, in the account currency") double amount) {
        return accountService.credit(accountId, amount);
    }
}
