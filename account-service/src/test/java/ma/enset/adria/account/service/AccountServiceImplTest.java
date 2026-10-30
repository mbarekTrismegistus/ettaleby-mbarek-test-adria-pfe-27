package ma.enset.adria.account.service;

import ma.enset.adria.account.domain.Account;
import ma.enset.adria.account.domain.AccountStatus;
import ma.enset.adria.account.dto.AccountMapper;
import ma.enset.adria.account.exception.AccountNotActiveException;
import ma.enset.adria.account.exception.AccountNotFoundException;
import ma.enset.adria.account.exception.InsufficientBalanceException;
import ma.enset.adria.account.exception.InvalidAmountException;
import ma.enset.adria.account.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    private AccountServiceImpl accountService;

    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImpl(accountRepository, new AccountMapper());
    }

    private Account account(AccountStatus status, double balance) {
        return Account.builder().id(id).customerReference("C1").currency("EUR").status(status).balance(balance).build();
    }

    @Test
    void debitDecreasesBalance() {
        when(accountRepository.findById(id)).thenReturn(Optional.of(account(AccountStatus.ACTIVE, 500)));
        assertThat(accountService.debit(id, 200).balance()).isEqualTo(300);
    }

    @Test
    void creditIncreasesBalance() {
        when(accountRepository.findById(id)).thenReturn(Optional.of(account(AccountStatus.ACTIVE, 500)));
        assertThat(accountService.credit(id, 200).balance()).isEqualTo(700);
    }

    @Test
    void debitFailsWhenBalanceInsufficient() {
        when(accountRepository.findById(id)).thenReturn(Optional.of(account(AccountStatus.ACTIVE, 100)));
        assertThatThrownBy(() -> accountService.debit(id, 200)).isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    void debitFailsWhenAccountBlocked() {
        when(accountRepository.findById(id)).thenReturn(Optional.of(account(AccountStatus.BLOCKED, 500)));
        assertThatThrownBy(() -> accountService.debit(id, 50)).isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    void debitFailsWhenAmountNotPositive() {
        assertThatThrownBy(() -> accountService.debit(id, 0)).isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void getByIdFailsWhenAccountMissing() {
        when(accountRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> accountService.getById(id)).isInstanceOf(AccountNotFoundException.class);
    }
}
