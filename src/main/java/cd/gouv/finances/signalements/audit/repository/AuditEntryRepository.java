package cd.gouv.finances.signalements.audit.repository;

import cd.gouv.finances.signalements.audit.entity.AuditEntry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> {
}
