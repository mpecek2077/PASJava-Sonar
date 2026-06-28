package pk.pm.pasir_pecek_maksymilian.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pk.pm.pasir_pecek_maksymilian.model.Transaction;
import pk.pm.pasir_pecek_maksymilian.model.User;
import java.util.List;


@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUser(User user);
    List<Transaction> findAllByUserAndTimestampGreaterThanEqual(User user, java.time.LocalDateTime timestamp);
    List<Transaction> findAllByUser(User user);
}