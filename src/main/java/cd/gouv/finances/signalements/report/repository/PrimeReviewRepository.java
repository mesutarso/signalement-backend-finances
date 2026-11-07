package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.PrimeReview;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrimeReviewRepository extends JpaRepository<PrimeReview, UUID> {
}
