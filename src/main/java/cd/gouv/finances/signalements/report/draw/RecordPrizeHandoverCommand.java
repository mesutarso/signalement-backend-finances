package cd.gouv.finances.signalements.report.draw;

import cd.gouv.finances.signalements.report.ReportRejected;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RecordPrizeHandoverCommand {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public RecordPrizeHandoverCommand(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public void execute(UUID prizeId, String note, UUID actorId) {
        var kept = note == null ? "" : note.trim();
        if (kept.length() > 500) {
            throw new ReportRejected("handover-note", 400);
        }
        var updated = jdbc.update("""
                update app.draw_prize
                set handed_at = ?, handed_by = ?, note = ?
                where id = ? and handed_at is null
                """, Timestamp.from(clock.instant()), actorId, kept.isEmpty() ? null : kept, prizeId);
        if (updated == 1) {
            return;
        }
        var present = jdbc.queryForObject("select count(*) from app.draw_prize where id = ?", Long.class, prizeId);
        if (present == null || present == 0) {
            throw new ReportRejected("handover-absent", 404);
        }
        throw new ReportRejected("handover-open", 409);
    }
}
