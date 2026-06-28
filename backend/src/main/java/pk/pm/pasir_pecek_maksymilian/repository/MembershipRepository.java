package pk.pm.pasir_pecek_maksymilian.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pk.pm.pasir_pecek_maksymilian.model.Membership;
import java.util.List;

public interface MembershipRepository extends JpaRepository<Membership, Long> {
    List<Membership> findByGroupId(Long groupId);
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
    void deleteByGroupId(Long groupId);
}