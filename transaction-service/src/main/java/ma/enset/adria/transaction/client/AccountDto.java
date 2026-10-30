package ma.enset.adria.transaction.client;

import java.util.UUID;

/**
 * Account as returned by account-service.
 */
public record AccountDto(
        UUID id,
        String customerReference,
        String status,
        String currency,
        double balance
) {
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
