package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.ReportAssignment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportAssignmentRepository extends JpaRepository<ReportAssignment, UUID> {

    List<ReportAssignment> findByReportIdOrderByRecordedAtDesc(UUID reportId);
}
