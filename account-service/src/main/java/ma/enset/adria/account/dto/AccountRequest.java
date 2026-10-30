package ma.enset.adria.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record AccountRequest(
        @NotBlank String customerReference,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code, e.g. EUR") String currency,
        @PositiveOrZero double initialBalance
) {
}
