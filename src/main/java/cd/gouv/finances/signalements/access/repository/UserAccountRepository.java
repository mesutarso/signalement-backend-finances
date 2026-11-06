package cd.gouv.finances.signalements.access.repository;

import cd.gouv.finances.signalements.access.Role;
import cd.gouv.finances.signalements.access.entity.UserAccount;
import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from UserAccount a where a.role = cd.gouv.finances.signalements.access.Role.ADMINISTRATOR order by a.id")
    List<UserAccount> lockAdministrators();

    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRoleAndEnabled(Role role, boolean enabled);

    List<UserAccount> findByRoleAndEnabledOrderByUsernameAsc(Role role, boolean enabled);

    Page<UserAccount> findAllByOrderByUsernameAsc(Pageable pageable);
}
