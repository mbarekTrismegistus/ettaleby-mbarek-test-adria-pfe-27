package ma.enset.adria.transaction.dto;

import java.util.UUID;

public record TransferResponse(
        UUID transferId,
        TransactionResponse debit,
        TransactionResponse credit
) {
}
