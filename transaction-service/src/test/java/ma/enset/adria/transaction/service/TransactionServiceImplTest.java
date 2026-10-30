package ma.enset.adria.transaction.service;

import ma.enset.adria.transaction.client.AccountDto;
import ma.enset.adria.transaction.client.AccountGateway;
import ma.enset.adria.transaction.config.BankProperties;
import ma.enset.adria.transaction.domain.Transaction;
import ma.enset.adria.transaction.domain.TransactionType;
import ma.enset.adria.transaction.dto.*;
import ma.enset.adria.transaction.exception.AccountNotActiveException;
import ma.enset.adria.transaction.exception.AccountServiceUnavailableException;
import ma.enset.adria.transaction.exception.BusinessRuleException;
import ma.enset.adria.transaction.exception.MaxAmountNotConfiguredException;
import ma.enset.adria.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    private static final UUID SOURCE = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountGateway accountGateway;

    private final BankProperties bankProperties = new BankProperties();
    private TransactionServiceImpl service;

    @BeforeEach
    void setUp() {
        bankProperties.setMaxAmount(new BigDecimal("1000"));
        service = new TransactionServiceImpl(transactionRepository, new TransactionMapper(), accountGateway, bankProperties);
    }

    private static AccountDto account(UUID id, String status, String currency, double balance) {
        return new AccountDto(id, "CUST", status, currency, balance);
    }

    private static PaymentRequest payment(String amount, String currency) {
        return new PaymentRequest(SOURCE, new BigDecimal(amount), currency, "GROCERY", "MA");
    }

    private static TransferRequest transfer(String amount) {
        return new TransferRequest(SOURCE, TARGET, new BigDecimal(amount), "EUR", "MA");
    }

    private void persistReturnsArgument() {
        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(transactionRepository.saveAndFlush(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void paymentIsSavedThenAccountDebited() {
        persistReturnsArgument();
        when(accountGateway.getAccount(SOURCE)).thenReturn(account(SOURCE, "ACTIVE", "EUR", 500));

        TransactionResponse response = service.createPayment(payment("250.00", "EUR"));

        assertThat(response.type()).isEqualTo(TransactionType.PAYMENT);
        assertThat(response.amount()).isEqualByComparingTo("250.00");
        var order = inOrder(transactionRepository, accountGateway);
        order.verify(transactionRepository).saveAndFlush(any(Transaction.class));
        order.verify(accountGateway).debit(SOURCE, new BigDecimal("250.00"));
    }

    @Test
    void paymentAboveCeilingIsRejectedWithoutCallingAccountService() {
        assertThatThrownBy(() -> service.createPayment(payment("1000.01", "EUR")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ceiling");
        verifyNoInteractions(accountGateway, transactionRepository);
    }

    @Test
    void paymentEqualToCeilingIsAccepted() {
        persistReturnsArgument();
        when(accountGateway.getAccount(SOURCE)).thenReturn(account(SOURCE, "ACTIVE", "EUR", 5000));
        assertThat(service.createPayment(payment("1000", "EUR")).amount()).isEqualByComparingTo("1000");
    }

    @Test
    void paymentFailsWhenCeilingNotConfigured() {
        bankProperties.setMaxAmount(null);
        assertThatThrownBy(() -> service.createPayment(payment("10", "EUR"))).isInstanceOf(MaxAmountNotConfiguredException.class);
    }

    @Test
    void paymentFailsOnBlockedAccount() {
        when(accountGateway.getAccount(SOURCE)).thenReturn(account(SOURCE, "BLOCKED", "EUR", 500));
        assertThatThrownBy(() -> service.createPayment(payment("10", "EUR"))).isInstanceOf(AccountNotActiveException.class);
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void paymentFailsOnCurrencyMismatch() {
        when(accountGateway.getAccount(SOURCE)).thenReturn(account(SOURCE, "ACTIVE", "MAD", 500));
        assertThatThrownBy(() -> service.createPayment(payment("10", "EUR")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    void paymentFailsOnInsufficientBalance() {
        when(accountGateway.getAccount(SOURCE)).thenReturn(account(SOURCE, "ACTIVE", "EUR", 50));
        assertThatThrownBy(() -> service.createPayment(payment("100", "EUR")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Insufficient balance");
        verify(accountGateway, never()).debit(any(), any());
    }

    @Test
    void paymentPropagatesDebitFailureSoTheTransactionRollsBack() {
        persistReturnsArgument();
        when(accountGateway.getAccount(SOURCE)).thenReturn(account(SOURCE, "ACTIVE", "EUR", 500));
        when(accountGateway.debit(any(), any())).thenThrow(new AccountServiceUnavailableException(new RuntimeException("down")));
        assertThatThrownBy(() -> service.createPayment(payment("10", "EUR"))).isInstanceOf(AccountServiceUnavailableException.class);
    }

    @Test
    void transferCreatesTwoLinkedLegsAndMovesMoney() {
        persistReturnsArgument();
        when(accountGateway.getAccount(SOURCE)).thenReturn(account(SOURCE, "ACTIVE", "EUR", 500));
        when(accountGateway.getAccount(TARGET)).thenReturn(account(TARGET, "ACTIVE", "EUR", 0));

        TransferResponse response = service.transfer(transfer("200"));

        assertThat(response.debit().type()).isEqualTo(TransactionType.TRANSFER_OUT);
        assertThat(response.credit().type()).isEqualTo(TransactionType.TRANSFER_IN);
        assertThat(response.debit().transferId()).isEqualTo(response.transferId()).isEqualTo(response.credit().transferId());
        verify(accountGateway).debit(SOURCE, new BigDecimal("200"));
        verify(accountGateway).credit(TARGET, new BigDecimal("200"));
    }

    @Test
    void transferToSameAccountIsRejected() {
        assertThatThrownBy(() -> service.transfer(new TransferRequest(SOURCE, SOURCE, BigDecimal.TEN, "EUR", null)))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(accountGateway);
    }

    @Test
    void transferToAccountInOtherCurrencyIsRejected() {
        when(accountGateway.getAccount(SOURCE)).thenReturn(account(SOURCE, "ACTIVE", "EUR", 500));
        when(accountGateway.getAccount(TARGET)).thenReturn(account(TARGET, "ACTIVE", "MAD", 0));
        assertThatThrownBy(() -> service.transfer(transfer("10"))).hasMessageContaining("Currency mismatch");
        verify(accountGateway, never()).debit(any(), any());
    }

    @Test
    void transferToBlockedAccountIsRejected() {
        when(accountGateway.getAccount(SOURCE)).thenReturn(account(SOURCE, "ACTIVE", "EUR", 500));
        when(accountGateway.getAccount(TARGET)).thenReturn(account(TARGET, "BLOCKED", "EUR", 0));
        assertThatThrownBy(() -> service.transfer(transfer("10"))).isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    void sourceIsRefundedWhenCreditFails() {
        persistReturnsArgument();
        when(accountGateway.getAccount(SOURCE)).thenReturn(account(SOURCE, "ACTIVE", "EUR", 500));
        when(accountGateway.getAccount(TARGET)).thenReturn(account(TARGET, "ACTIVE", "EUR", 0));
        when(accountGateway.credit(TARGET, new BigDecimal("200")))
                .thenThrow(new AccountNotActiveException("Account is not active: " + TARGET));

        assertThatThrownBy(() -> service.transfer(transfer("200"))).isInstanceOf(AccountNotActiveException.class);
        verify(accountGateway).credit(SOURCE, new BigDecimal("200"));
    }
}
