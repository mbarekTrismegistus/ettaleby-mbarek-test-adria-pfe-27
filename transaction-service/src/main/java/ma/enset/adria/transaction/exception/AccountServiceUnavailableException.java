package ma.enset.adria.transaction.exception;

public class AccountServiceUnavailableException extends RuntimeException {
    public AccountServiceUnavailableException(Throwable cause) {
        super("Account service is unavailable, please retry later", cause);
    }
}
