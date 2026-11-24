package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.Qualification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QualificationRepository extends JpaRepository<Qualification, UUID> {

    List<Qualification> findByReportIdOrderByRecordedAtDesc(UUID reportId);

    Optional<Qualification> findFirstByReportIdOrderByRecordedAtDescIdDesc(UUID reportId);

}
