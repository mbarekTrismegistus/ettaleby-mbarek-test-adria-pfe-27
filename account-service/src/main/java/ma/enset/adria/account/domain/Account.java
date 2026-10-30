package ma.enset.adria.account.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String customerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(nullable = false, length = 3)
    private String currency;

    private double balance;

    // optimistic locking: protects the balance against concurrent updates
    @Version
    private Long version;

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }
}
