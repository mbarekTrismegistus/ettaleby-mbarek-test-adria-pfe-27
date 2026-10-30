package ma.enset.adria.transaction.exception;

public class MaxAmountNotConfiguredException extends RuntimeException {
    public MaxAmountNotConfiguredException() {
        super("Transaction ceiling (bank.transaction.max-amount) is not configured: is config-service reachable?");
    }
}
