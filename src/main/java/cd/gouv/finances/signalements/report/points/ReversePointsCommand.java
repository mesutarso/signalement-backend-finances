package cd.gouv.finances.signalements.report.points;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.PointMovement;
import cd.gouv.finances.signalements.report.prime.AssessPrimeCommand;
import cd.gouv.finances.signalements.report.repository.PointMovementRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReversePointsCommand {

    private final PointsContext pointsContext;
    private final PointMovementRepository pointMovementRepository;
    private final AssessPrimeCommand assessPrimeCommand;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public List<AssessPrimeCommand.Change> execute(UUID reportId, String reason, UUID actorId) {
        var keptReason = reason == null ? "" : reason.trim();
        if (keptReason.isEmpty() || keptReason.length() > 500) {
            throw new ReportRejected("points-reason", 400);
        }
        pointsContext.lock();
        var balance = pointsContext.balance(reportId);
        if (balance.base() + balance.bonus() <= 0) {
            throw new ReportRejected("points-absent", 404);
        }
        pointMovementRepository.save(new PointMovement(
                UUID.randomUUID(),
                reportId,
                balance.declarantId(),
                balance.taxId(),
                balance.finding(),
                -balance.base(),
                -balance.bonus(),
                PointMovement.REVERSAL,
                balance.ruleVersion(),
                null,
                keptReason,
                balance.filedOn(),
                actorId,
                clock.instant()));
        pointMovementRepository.flush();
        return assessPrimeCommand.execute(balance.declarantId(), actorId, balance.ruleVersion());
    }
}
