package ma.enset.adria.transaction.repository;

import ma.enset.adria.transaction.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    List<Transaction> findByTransferId(UUID transferId);

    List<Transaction> findAllByOrderByCreatedAtDesc();
}
