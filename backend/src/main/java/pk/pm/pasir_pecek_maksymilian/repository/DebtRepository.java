package pk.pm.pasir_pecek_maksymilian.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pk.pm.pasir_pecek_maksymilian.model.Debt;
import java.util.List;

public interface DebtRepository extends JpaRepository<Debt, Long> {
    List<Debt> findByGroupId(Long groupId);
    void deleteByGroupId(Long groupId);
}