package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.PointMovement;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointMovementRepository extends JpaRepository<PointMovement, UUID> {
}
