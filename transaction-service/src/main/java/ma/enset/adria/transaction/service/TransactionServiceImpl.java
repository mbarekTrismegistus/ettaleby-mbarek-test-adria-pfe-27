package ma.enset.adria.transaction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.enset.adria.transaction.client.AccountDto;
import ma.enset.adria.transaction.client.AccountGateway;
import ma.enset.adria.transaction.config.BankProperties;
import ma.enset.adria.transaction.domain.Transaction;
import ma.enset.adria.transaction.domain.TransactionType;
import ma.enset.adria.transaction.dto.*;
import ma.enset.adria.transaction.exception.AccountNotActiveException;
import ma.enset.adria.transaction.exception.BusinessRuleException;
import ma.enset.adria.transaction.exception.MaxAmountNotConfiguredException;
import ma.enset.adria.transaction.exception.TransactionNotFoundException;
import ma.enset.adria.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Validated
@Transactional
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private static final String TRANSFER_CATEGORY = "TRANSFER";

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final AccountGateway accountGateway;
    private final BankProperties bankProperties;

    /**
     * Rules: amount > 0 and <= ceiling, account exists and is ACTIVE, same currency, sufficient balance.
     * The transaction is saved first, then the balance is updated; if the debit fails the local
     * transaction is rolled back, so no transaction is ever stored without its balance update.
     */
    @Override
    public TransactionResponse createPayment(PaymentRequest request) {
        checkAmount(request.amount());
        AccountDto account = accountGateway.getAccount(request.accountId());
        checkCanBeDebited(account, request.currency(), request.amount());

        Transaction transaction = transactionRepository.saveAndFlush(Transaction.builder()
                .id(UUID.randomUUID())
                .accountId(account.id())
                .amount(request.amount().setScale(2))
                .currency(request.currency())
                .merchantCategory(request.merchantCategory())
                .country(request.country())
                .type(TransactionType.PAYMENT)
                .build());

        accountGateway.debit(account.id(), request.amount());
        log.info("Payment {} of {} {} recorded on account {}", transaction.getId(), request.amount(), request.currency(), account.id());
        return transactionMapper.toResponse(transaction);
    }

    /**
     * Stored as two linked transactions (TRANSFER_OUT / TRANSFER_IN) sharing a transferId.
     * If crediting the target fails after the source was debited, the source is refunded (compensation).
     */
    @Override
    public TransferResponse transfer(TransferRequest request) {
        if (request.sourceAccountId().equals(request.targetAccountId())) {
            throw new BusinessRuleException("Source and target accounts must be different");
        }
        checkAmount(request.amount());
        AccountDto source = accountGateway.getAccount(request.sourceAccountId());
        AccountDto target = accountGateway.getAccount(request.targetAccountId());
        checkCanBeDebited(source, request.currency(), request.amount());
        checkActive(target);
        checkCurrency(target, request.currency());

        UUID transferId = UUID.randomUUID();
        Transaction debit = transactionRepository.save(transferLeg(transferId, source.id(), TransactionType.TRANSFER_OUT, request));
        Transaction credit = transactionRepository.save(transferLeg(transferId, target.id(), TransactionType.TRANSFER_IN, request));
        transactionRepository.flush();

        accountGateway.debit(source.id(), request.amount());
        try {
            accountGateway.credit(target.id(), request.amount());
        } catch (RuntimeException creditFailure) {
            refund(source.id(), request.amount(), transferId);
            throw creditFailure;
        }
        log.info("Transfer {} of {} {} from {} to {}", transferId, request.amount(), request.currency(), source.id(), target.id());
        return new TransferResponse(transferId, transactionMapper.toResponse(debit), transactionMapper.toResponse(credit));
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getById(UUID id) {
        return transactionRepository.findById(id)
                .map(transactionMapper::toResponse)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getAll() {
        return transactionRepository.findAllByOrderByCreatedAtDesc().stream().map(transactionMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getByAccount(UUID accountId) {
        return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(transactionMapper::toResponse)
                .toList();
    }

    @Override
    public BigDecimal getMaxAmount() {
        BigDecimal maxAmount = bankProperties.getMaxAmount();
        if (maxAmount == null) {
            throw new MaxAmountNotConfiguredException();
        }
        return maxAmount;
    }

    private void checkAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("Amount must be strictly positive");
        }
        BigDecimal maxAmount = getMaxAmount();
        if (amount.compareTo(maxAmount) > 0) {
            throw new BusinessRuleException("Amount %s exceeds the configured ceiling of %s".formatted(amount, maxAmount));
        }
    }

    private void checkCanBeDebited(AccountDto account, String currency, BigDecimal amount) {
        checkActive(account);
        checkCurrency(account, currency);
        if (BigDecimal.valueOf(account.balance()).compareTo(amount) < 0) {
            throw new BusinessRuleException("Insufficient balance on account %s: balance=%.2f, requested=%s"
                    .formatted(account.id(), account.balance(), amount));
        }
    }

    private void checkActive(AccountDto account) {
        if (!account.isActive()) {
            throw new AccountNotActiveException("Account is not active: " + account.id());
        }
    }

    private void checkCurrency(AccountDto account, String currency) {
        if (!account.currency().equals(currency)) {
            throw new BusinessRuleException("Currency mismatch: account %s is in %s, transaction is in %s"
                    .formatted(account.id(), account.currency(), currency));
        }
    }

    private Transaction transferLeg(UUID transferId, UUID accountId, TransactionType type, TransferRequest request) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .amount(request.amount().setScale(2))
                .currency(request.currency())
                .merchantCategory(TRANSFER_CATEGORY)
                .country(request.country())
                .type(type)
                .transferId(transferId)
                .build();
    }

    private void refund(UUID sourceId, BigDecimal amount, UUID transferId) {
        try {
            accountGateway.credit(sourceId, amount);
            log.warn("Transfer {} failed on credit, source account {} refunded", transferId, sourceId);
        } catch (RuntimeException refundFailure) {
            log.error("Transfer {}: refund of {} to account {} FAILED, manual reconciliation required",
                    transferId, amount, sourceId, refundFailure);
        }
    }
}
