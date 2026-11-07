package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.AbuseNote;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AbuseNoteRepository extends JpaRepository<AbuseNote, UUID> {
}
