package ma.enset.adria.transaction.exception;

public class AccountNotActiveException extends BusinessException {
    public AccountNotActiveException(String message) {
        super(message);
    }
}
