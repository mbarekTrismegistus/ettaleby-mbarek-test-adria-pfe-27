package ma.enset.adria.transaction.exception;

public class AccountNotFoundException extends BusinessException {
    public AccountNotFoundException(String message) {
        super(message);
    }
}
