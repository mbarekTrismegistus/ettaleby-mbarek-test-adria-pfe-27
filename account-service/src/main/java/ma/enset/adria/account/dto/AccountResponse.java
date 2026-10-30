package ma.enset.adria.account.dto;

import ma.enset.adria.account.domain.AccountStatus;

import java.util.UUID;

public record AccountResponse(
        UUID id,
        String customerReference,
        AccountStatus status,
        String currency,
        double balance
) {
}
