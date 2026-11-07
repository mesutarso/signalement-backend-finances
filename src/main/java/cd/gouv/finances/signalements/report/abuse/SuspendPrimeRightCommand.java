package cd.gouv.finances.signalements.report.abuse;

import cd.gouv.finances.signalements.report.ReportReference;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.prime.AssessPrimeCommand;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SuspendPrimeRightCommand {

    private final JdbcTemplate jdbc;
    private final ReportReference reportReference;
    private final AssessPrimeCommand assessPrimeCommand;
    private final Clock clock;

    public SuspendPrimeRightCommand(
            JdbcTemplate jdbc,
            ReportReference reportReference,
            AssessPrimeCommand assessPrimeCommand,
            Clock clock) {
        this.jdbc = jdbc;
        this.reportReference = reportReference;
        this.assessPrimeCommand = assessPrimeCommand;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public String execute(String reference, String action, String reason, UUID actorId) {
        var decision = action == null ? "" : action.trim();
        if (!"suspended".equals(decision) && !"restored".equals(decision)) {
            throw new ReportRejected("suspension-action", 400);
        }
        var keptReason = reason == null ? "" : reason.trim();
        if (keptReason.isEmpty() || keptReason.length() > 500) {
            throw new ReportRejected("suspension-reason", 400);
        }
        var match = reportReference.require(reference);
        var declarants = jdbc.query(
                "select nullif(btrim(reporter_user_id), '') from app.report where id = ?",
                (row, number) -> row.getString(1),
                match.id());
        var declarant = declarants.isEmpty() ? null : declarants.getFirst();
        if (declarant == null) {
            throw new ReportRejected("suspension-declarant", 409);
        }
        var latest = jdbc.query("""
                select action from app.prime_suspension
                where declarant_id = ?
                order by recorded_at desc
                limit 1
                """, (row, number) -> row.getString(1), declarant);
        if (!latest.isEmpty() && decision.equals(latest.getFirst())) {
            throw new ReportRejected("suspension-state", 409);
        }
        jdbc.update("""
                insert into app.prime_suspension (id, declarant_id, action, reason, actor_id, recorded_at)
                values (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), declarant, decision, keptReason, actorId, java.sql.Timestamp.from(clock.instant()));
        assessPrimeCommand.execute(declarant, actorId);
        return match.reference();
    }
}
