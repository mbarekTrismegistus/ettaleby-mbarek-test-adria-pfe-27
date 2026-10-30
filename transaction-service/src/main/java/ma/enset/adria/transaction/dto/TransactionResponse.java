package ma.enset.adria.transaction.dto;

import ma.enset.adria.transaction.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID accountId,
        BigDecimal amount,
        String currency,
        String merchantCategory,
        String country,
        Instant createdAt,
        TransactionType type,
        UUID transferId
) {
}
