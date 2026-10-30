package ma.enset.adria.transaction.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Bound from the config-service (bank.transaction.max-amount).
 * Rebound on /actuator/refresh, so the ceiling can change without a restart.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "bank.transaction")
public class BankProperties {

    private BigDecimal maxAmount;
}
