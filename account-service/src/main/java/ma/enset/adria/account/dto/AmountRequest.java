package ma.enset.adria.account.dto;

import jakarta.validation.constraints.Positive;

public record AmountRequest(@Positive double amount) {
}
