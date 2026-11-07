package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.DecisionCorrection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionCorrectionRepository extends JpaRepository<DecisionCorrection, UUID> {

    boolean existsByQualificationId(UUID qualificationId);
}
