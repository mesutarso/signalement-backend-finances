package cd.gouv.finances.signalements.report.command;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.duplicate.PrepareDuplicateAlerts;
import cd.gouv.finances.signalements.report.entity.DuplicateAlert;
import cd.gouv.finances.signalements.report.repository.DuplicateAlertRepository;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DecideDuplicateCommand {

    private static final Set<String> DECISIONS = Set.of("confirm", "dismiss");

    private final DuplicateAlertRepository duplicateAlertRepository;
    private final PrepareDuplicateAlerts prepareDuplicateAlerts;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public void execute(UUID reportId, UUID alertId, String decision, String reason, UUID actorId) {
        if (!DECISIONS.contains(decision)) {
            throw new ReportRejected("duplicate-decision", 400);
        }
        var alert = duplicateAlertRepository.findById(alertId)
                .filter(found -> found.getReportId().equals(reportId) || found.getOtherReportId().equals(reportId))
                .orElseThrow(() -> new ReportRejected("duplicate-absent", 404));
        var chosen = "confirm".equals(decision) ? DuplicateAlert.CONFIRMED : DuplicateAlert.DISMISSED;
        if (!DuplicateAlert.OPEN.equals(alert.getStatus())) {
            if (chosen.equals(alert.getStatus())) {
                return;
            }
            throw new ReportRejected("duplicate-decided", 409);
        }
        if (DuplicateAlert.DISMISSED.equals(chosen)) {
            alert.dismiss(reason.trim(), actorId, clock.instant());
            return;
        }
        var winner = prepareDuplicateAlerts.winner(alert.getReportId(), alert.getOtherReportId());
        if (winner == null) {
            throw new ReportRejected("report-absent", 404);
        }
        alert.confirm(reason.trim(), actorId, winner.id(), clock.instant());
    }
}
