package ma.enset.adria.transaction.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(
        @NotNull UUID accountId,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code, e.g. EUR") String currency,
        @NotBlank String merchantCategory,
        @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "must be an ISO 3166 alpha-2 code, e.g. MA") String country
) {
}
