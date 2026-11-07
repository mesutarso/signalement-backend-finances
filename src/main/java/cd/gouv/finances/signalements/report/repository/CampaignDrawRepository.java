package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.CampaignDraw;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignDrawRepository extends JpaRepository<CampaignDraw, UUID> {

    Optional<CampaignDraw> findByKindAndPeriodStart(String kind, LocalDate periodStart);

    boolean existsByKind(String kind);
}
