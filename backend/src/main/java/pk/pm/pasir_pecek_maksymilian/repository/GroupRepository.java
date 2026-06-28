package pk.pm.pasir_pecek_maksymilian.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pk.pm.pasir_pecek_maksymilian.model.Group;
import pk.pm.pasir_pecek_maksymilian.model.User;
import java.util.List;

public interface GroupRepository extends JpaRepository<Group, Long> {
    List<Group> findByMemberships_User(User user);
}