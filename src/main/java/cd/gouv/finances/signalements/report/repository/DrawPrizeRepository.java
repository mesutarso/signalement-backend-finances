package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.DrawPrize;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrawPrizeRepository extends JpaRepository<DrawPrize, UUID> {
}
