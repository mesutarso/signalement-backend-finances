package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.DuplicateAlert;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DuplicateAlertRepository extends JpaRepository<DuplicateAlert, UUID> {

    Optional<DuplicateAlert> findByReportIdAndOtherReportIdAndCriterion(
            UUID reportId, UUID otherReportId, String criterion);

    List<DuplicateAlert> findByReportIdOrOtherReportIdOrderByCreatedAtDesc(UUID reportId, UUID otherReportId);
}
