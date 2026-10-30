package ma.enset.adria.account.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.enset.adria.account.domain.Account;
import ma.enset.adria.account.domain.AccountStatus;
import ma.enset.adria.account.dto.AccountMapper;
import ma.enset.adria.account.dto.AccountRequest;
import ma.enset.adria.account.dto.AccountResponse;
import ma.enset.adria.account.exception.AccountNotActiveException;
import ma.enset.adria.account.exception.AccountNotFoundException;
import ma.enset.adria.account.exception.InsufficientBalanceException;
import ma.enset.adria.account.exception.InvalidAmountException;
import ma.enset.adria.account.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;

    @Override
    public AccountResponse create(AccountRequest request) {
        Account account = Account.builder()
                .id(UUID.randomUUID())
                .customerReference(request.customerReference())
                .currency(request.currency())
                .balance(request.initialBalance())
                .status(AccountStatus.ACTIVE)
                .build();
        Account saved = accountRepository.save(account);
        log.info("Account created: {}", saved.getId());
        return accountMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getById(UUID id) {
        return accountMapper.toResponse(findAccount(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getAll() {
        return accountRepository.findAll().stream().map(accountMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getByCustomerReference(String customerReference) {
        return accountRepository.findByCustomerReference(customerReference).stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    @Override
    public AccountResponse update(UUID id, AccountRequest request) {
        Account account = findAccount(id);
        account.setCustomerReference(request.customerReference());
        account.setCurrency(request.currency());
        return accountMapper.toResponse(account);
    }

    @Override
    public void delete(UUID id) {
        accountRepository.delete(findAccount(id));
    }

    @Override
    public AccountResponse changeStatus(UUID id, AccountStatus status) {
        Account account = findAccount(id);
        account.setStatus(status);
        log.info("Account {} status changed to {}", id, status);
        return accountMapper.toResponse(account);
    }

    @Override
    public AccountResponse debit(UUID id, double amount) {
        Account account = findActiveAccount(id, amount);
        if (account.getBalance() < amount) {
            throw new InsufficientBalanceException(id, account.getBalance(), amount);
        }
        account.setBalance(account.getBalance() - amount);
        log.info("Account {} debited {} -> balance {}", id, amount, account.getBalance());
        return accountMapper.toResponse(account);
    }

    @Override
    public AccountResponse credit(UUID id, double amount) {
        Account account = findActiveAccount(id, amount);
        account.setBalance(account.getBalance() + amount);
        log.info("Account {} credited {} -> balance {}", id, amount, account.getBalance());
        return accountMapper.toResponse(account);
    }

    private Account findAccount(UUID id) {
        return accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    private Account findActiveAccount(UUID id, double amount) {
        if (amount <= 0) {
            throw new InvalidAmountException(amount);
        }
        Account account = findAccount(id);
        if (!account.isActive()) {
            throw new AccountNotActiveException(id);
        }
        return account;
    }
}
