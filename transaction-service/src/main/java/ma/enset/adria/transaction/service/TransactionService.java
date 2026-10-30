package ma.enset.adria.transaction.service;

import jakarta.validation.Valid;
import ma.enset.adria.transaction.dto.PaymentRequest;
import ma.enset.adria.transaction.dto.TransactionResponse;
import ma.enset.adria.transaction.dto.TransferRequest;
import ma.enset.adria.transaction.dto.TransferResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Single business layer shared by the REST, GraphQL and MCP adapters.
 */
public interface TransactionService {

    TransactionResponse createPayment(@Valid PaymentRequest request);

    TransferResponse transfer(@Valid TransferRequest request);

    TransactionResponse getById(UUID id);

    List<TransactionResponse> getAll();

    List<TransactionResponse> getByAccount(UUID accountId);

    BigDecimal getMaxAmount();
}
