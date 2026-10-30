package ma.enset.adria.transaction.web.graphql;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.enset.adria.transaction.dto.PaymentRequest;
import ma.enset.adria.transaction.dto.TransactionResponse;
import ma.enset.adria.transaction.dto.TransferRequest;
import ma.enset.adria.transaction.dto.TransferResponse;
import ma.enset.adria.transaction.service.TransactionService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class TransactionGraphQLController {

    private final TransactionService transactionService;

    @QueryMapping
    public List<TransactionResponse> transactions() {
        return transactionService.getAll();
    }

    @QueryMapping
    public TransactionResponse transactionById(@Argument UUID id) {
        return transactionService.getById(id);
    }

    @QueryMapping
    public List<TransactionResponse> transactionsByAccount(@Argument UUID accountId) {
        return transactionService.getByAccount(accountId);
    }

    @QueryMapping
    public BigDecimal maxTransactionAmount() {
        return transactionService.getMaxAmount();
    }

    @MutationMapping
    public TransactionResponse createPayment(@Argument @Valid PaymentRequest input) {
        return transactionService.createPayment(input);
    }

    @MutationMapping
    public TransferResponse transfer(@Argument @Valid TransferRequest input) {
        return transactionService.transfer(input);
    }
}
