package ma.enset.adria.transaction.web.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.enset.adria.transaction.dto.PaymentRequest;
import ma.enset.adria.transaction.dto.TransactionResponse;
import ma.enset.adria.transaction.dto.TransferRequest;
import ma.enset.adria.transaction.dto.TransferResponse;
import ma.enset.adria.transaction.service.TransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionRestController {

    private final TransactionService transactionService;

    @GetMapping
    public List<TransactionResponse> getAll(@RequestParam(required = false) UUID accountId) {
        return accountId == null ? transactionService.getAll() : transactionService.getByAccount(accountId);
    }

    @GetMapping("/{id}")
    public TransactionResponse getById(@PathVariable UUID id) {
        return transactionService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createPayment(@Valid @RequestBody PaymentRequest request) {
        return transactionService.createPayment(request);
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(@Valid @RequestBody TransferRequest request) {
        return transactionService.transfer(request);
    }

    @GetMapping("/limits")
    public Map<String, BigDecimal> limits() {
        return Map.of("maxAmount", transactionService.getMaxAmount());
    }
}
