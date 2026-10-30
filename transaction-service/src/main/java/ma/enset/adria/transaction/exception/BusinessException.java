package ma.enset.adria.transaction.exception;

/**
 * Functional error: the request was understood but refused by a business rule.
 */
public abstract class BusinessException extends RuntimeException {
    protected BusinessException(String message) {
        super(message);
    }
}
