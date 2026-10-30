package ma.enset.adria.account.dto;

import jakarta.validation.constraints.NotNull;
import ma.enset.adria.account.domain.AccountStatus;

public record StatusRequest(@NotNull AccountStatus status) {
}
