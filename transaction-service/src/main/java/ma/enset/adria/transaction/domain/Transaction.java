package ma.enset.adria.transaction.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions", indexes = @Index(name = "idx_transaction_account", columnList = "accountId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    private String merchantCategory;

    private String country;

    @Column(nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    // links the two legs (TRANSFER_OUT / TRANSFER_IN) of a transfer, null for payments
    private UUID transferId;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
