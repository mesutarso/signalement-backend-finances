package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.DeclarantMessage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeclarantMessageRepository extends JpaRepository<DeclarantMessage, UUID> {

    boolean existsByReportIdAndKind(UUID reportId, String kind);
}
