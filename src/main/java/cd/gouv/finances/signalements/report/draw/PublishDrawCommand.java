package cd.gouv.finances.signalements.report.draw;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.CampaignDraw;
import cd.gouv.finances.signalements.report.repository.CampaignDrawRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PublishDrawCommand {

    private final CampaignDrawRepository campaignDrawRepository;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public void execute(UUID id, UUID actorId) {
        var draw = campaignDrawRepository.findById(id).orElseThrow(() -> new ReportRejected("draw-absent", 404));
        if (CampaignDraw.PUBLISHED.equals(draw.getStatus())) {
            return;
        }
        if (!CampaignDraw.DRAWN.equals(draw.getStatus())) {
            throw new ReportRejected("draw-state", 409);
        }
        draw.publish(actorId, clock.instant());
    }
}
