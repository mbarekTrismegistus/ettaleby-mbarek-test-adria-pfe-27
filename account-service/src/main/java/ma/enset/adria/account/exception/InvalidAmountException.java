package ma.enset.adria.account.exception;

public class InvalidAmountException extends RuntimeException {
    public InvalidAmountException(double amount) {
        super("Amount must be strictly positive: " + amount);
    }
}
