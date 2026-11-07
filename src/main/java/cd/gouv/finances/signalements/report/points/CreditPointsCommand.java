package cd.gouv.finances.signalements.report.points;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.PointMovement;
import cd.gouv.finances.signalements.report.prime.AssessPrimeCommand;
import cd.gouv.finances.signalements.report.repository.PointMovementRepository;
import cd.gouv.finances.signalements.report.rule.ActiveRule;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreditPointsCommand {

    private final PointsContext pointsContext;
    private final PointMovementRepository pointMovementRepository;
    private final AssessPrimeCommand assessPrimeCommand;
    private final ActiveRule activeRule;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public List<AssessPrimeCommand.Change> execute(UUID reportId, String reference, UUID actorId) {
        var keptReference = reference == null ? "" : reference.trim();
        if (keptReference.isEmpty() || keptReference.length() > 80) {
            throw new ReportRejected("points-reference", 400);
        }
        var claim = pointsContext.claim(reportId);
        var rule = activeRule.requireActive();
        var award = PointsAward.assess(claim, rule.scale());
        pointMovementRepository.save(new PointMovement(
                UUID.randomUUID(),
                reportId,
                claim.declarantId().trim(),
                claim.taxId().trim(),
                claim.finding(),
                award.base(),
                award.bonus(),
                PointMovement.CREDIT,
                rule.code(),
                keptReference,
                null,
                claim.filedOn(),
                actorId,
                clock.instant()));
        pointMovementRepository.flush();
        return assessPrimeCommand.execute(claim.declarantId().trim(), actorId, rule.code());
    }
}
