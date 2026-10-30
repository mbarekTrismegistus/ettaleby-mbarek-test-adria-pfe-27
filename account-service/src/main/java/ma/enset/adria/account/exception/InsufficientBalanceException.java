package ma.enset.adria.account.exception;

import java.util.UUID;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(UUID id, double balance, double amount) {
        super("Insufficient balance on account %s: balance=%.2f, requested=%.2f".formatted(id, balance, amount));
    }
}
