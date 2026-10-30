package ma.enset.adria.transaction.mcp;

import lombok.RequiredArgsConstructor;
import ma.enset.adria.transaction.dto.PaymentRequest;
import ma.enset.adria.transaction.dto.TransactionResponse;
import ma.enset.adria.transaction.dto.TransferRequest;
import ma.enset.adria.transaction.dto.TransferResponse;
import ma.enset.adria.transaction.service.TransactionService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * MCP adapter: exposes the transaction service as tools for the AI agent.
 */
@Component
@RequiredArgsConstructor
public class TransactionMcpTools {

    private final TransactionService transactionService;

    @McpTool(name = "createPayment", description = "Record a card payment on an account: checks the account is ACTIVE, the currency matches, "
            + "the balance is sufficient and the amount is under the configured ceiling, then debits the account.")
    public TransactionResponse createPayment(
            @McpToolParam(description = "UUID of the account to debit") UUID accountId,
            @McpToolParam(description = "Strictly positive amount") BigDecimal amount,
            @McpToolParam(description = "ISO 4217 currency code in upper case, must match the account currency, e.g. EUR") String currency,
            @McpToolParam(description = "Merchant category, e.g. GROCERY, TRAVEL, ELECTRONICS, RESTAURANT") String merchantCategory,
            @McpToolParam(description = "ISO 3166 alpha-2 country code of the merchant, e.g. MA, FR") String country) {
        return transactionService.createPayment(new PaymentRequest(accountId, amount, currency, merchantCategory, country));
    }

    @McpTool(name = "transfer", description = "Transfer money from one account to another account with the same currency. "
            + "Creates a TRANSFER_OUT and a TRANSFER_IN transaction and updates both balances.")
    public TransferResponse transfer(
            @McpToolParam(description = "UUID of the account to debit") UUID sourceAccountId,
            @McpToolParam(description = "UUID of the account to credit") UUID targetAccountId,
            @McpToolParam(description = "Strictly positive amount") BigDecimal amount,
            @McpToolParam(description = "ISO 4217 currency code of both accounts, e.g. EUR") String currency,
            @McpToolParam(description = "ISO 3166 alpha-2 country code, e.g. MA", required = false) String country) {
        return transactionService.transfer(new TransferRequest(sourceAccountId, targetAccountId, amount, currency, country));
    }

    @McpTool(name = "getTransaction", description = "Get a transaction by its UUID.")
    public TransactionResponse getTransaction(
            @McpToolParam(description = "Transaction UUID") UUID transactionId) {
        return transactionService.getById(transactionId);
    }

    @McpTool(name = "listAccountTransactions", description = "List the transactions (payments and transfers) of an account, most recent first.")
    public List<TransactionResponse> listAccountTransactions(
            @McpToolParam(description = "Account UUID") UUID accountId) {
        return transactionService.getByAccount(accountId);
    }

    @McpTool(name = "listTransactions", description = "List all transactions of the bank, most recent first.")
    public List<TransactionResponse> listTransactions() {
        return transactionService.getAll();
    }

    @McpTool(name = "getMaxTransactionAmount", description = "Get the maximum amount allowed for a single transaction.")
    public BigDecimal getMaxTransactionAmount() {
        return transactionService.getMaxAmount();
    }
}
