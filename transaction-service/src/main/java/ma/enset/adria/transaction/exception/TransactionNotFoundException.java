package ma.enset.adria.transaction.exception;

import java.util.UUID;

public class TransactionNotFoundException extends BusinessException {
    public TransactionNotFoundException(UUID id) {
        super("Transaction not found: " + id);
    }
}
