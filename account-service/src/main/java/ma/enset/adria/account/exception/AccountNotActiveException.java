package ma.enset.adria.account.exception;

import java.util.UUID;

public class AccountNotActiveException extends RuntimeException {
    public AccountNotActiveException(UUID id) {
        super("Account is not active: " + id);
    }
}
