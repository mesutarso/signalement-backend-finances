package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.SupervisionReview;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupervisionReviewRepository extends JpaRepository<SupervisionReview, UUID> {

    List<SupervisionReview> findByReportIdOrderByRecordedAtDesc(UUID reportId);

    Optional<SupervisionReview> findFirstByQualificationIdOrderByRecordedAtDesc(UUID qualificationId);
}
