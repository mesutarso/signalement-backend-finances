package cd.gouv.finances.signalements.report.correction;

import cd.gouv.finances.signalements.report.ReportReference;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.DecisionCorrection;
import cd.gouv.finances.signalements.report.points.PointsContext;
import cd.gouv.finances.signalements.report.points.ReversePointsCommand;
import cd.gouv.finances.signalements.report.repository.DecisionCorrectionRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FallConfirmationCommand {

    private final JdbcTemplate jdbc;
    private final ReportReference reportReference;
    private final DecisionCorrectionRepository decisionCorrectionRepository;
    private final PointsContext pointsContext;
    private final ReversePointsCommand reversePointsCommand;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public UUID execute(String reference, String reason, UUID actorId) {
        var keptReason = reason == null ? "" : reason.trim();
        if (keptReason.isEmpty() || keptReason.length() > 500) {
            throw new ReportRejected("correction-reason", 400);
        }
        var report = reportReference.require(reference);
        var rows = jdbc.query("""
                select id, decision
                from app.qualification
                where report_id = ?
                order by recorded_at desc, id desc
                limit 1
                """, (row, number) -> new Latest(row.getObject(1, UUID.class), row.getString(2)), report.id());
        if (rows.isEmpty() || !"confirm".equals(rows.getFirst().decision())) {
            throw new ReportRejected("correction-state", 409);
        }
        var qualificationId = rows.getFirst().id();
        if (decisionCorrectionRepository.existsByQualificationId(qualificationId)) {
            throw new ReportRejected("correction-open", 409);
        }
        var now = clock.instant();
        var correction = new DecisionCorrection(UUID.randomUUID(), report.id(), qualificationId, keptReason, actorId, now);
        decisionCorrectionRepository.save(correction);
        if (pointsContext.net(report.id()) > 0) {
            reversePointsCommand.execute(report.id(), keptReason, actorId);
        }
        jdbc.update("""
                update app.draw_ticket ticket
                set excluded_at = ?
                where ticket.report_id = ?
                  and ticket.excluded_at is null
                  and not exists (select 1 from app.draw_prize prize where prize.ticket_id = ticket.id)
                """, Timestamp.from(now), report.id());
        return correction.getId();
    }

    private record Latest(UUID id, String decision) {
    }
}
