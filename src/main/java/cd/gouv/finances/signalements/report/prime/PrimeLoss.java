package cd.gouv.finances.signalements.report.prime;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PrimeLoss {

    public static final int DAYS = 90;

    private static final ZoneId KINSHASA = ZoneId.of("Africa/Kinshasa");

    private final JdbcTemplate jdbc;

    public PrimeLoss(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static boolean due(LocalDate creditOn, LocalDate today, boolean ready, boolean alreadyLost) {
        if (alreadyLost) {
            return true;
        }
        if (ready || creditOn == null || today == null) {
            return false;
        }
        return today.isAfter(creditOn.plusDays(DAYS));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean capture(String declarantId, Instant now) {
        var rows = jdbc.query("""
                select lost_at, state,
                       (select min(movement.recorded_at)
                        from app.point_movement movement
                        join app.report report on report.id = movement.report_id
                        where btrim(report.reporter_user_id) = payout.declarant_id
                          and movement.kind = 'credit')
                from app.payout_number payout
                where payout.declarant_id = ?
                """, (row, number) -> new Snapshot(
                row.getTimestamp(1) != null,
                PrimeContext.READY.equals(row.getString(2)),
                row.getTimestamp(3) == null ? null : row.getTimestamp(3).toInstant().atZone(KINSHASA).toLocalDate()), declarantId);
        if (rows.isEmpty()) {
            return false;
        }
        var snapshot = rows.get(0);
        var today = now.atZone(KINSHASA).toLocalDate();
        if (!due(snapshot.creditOn(), today, snapshot.ready(), snapshot.alreadyLost())) {
            return false;
        }
        if (!snapshot.alreadyLost()) {
            jdbc.update(
                    "update app.payout_number set lost_at = ? where declarant_id = ? and lost_at is null",
                    Timestamp.from(now),
                    declarantId);
        }
        return true;
    }

    private record Snapshot(boolean alreadyLost, boolean ready, LocalDate creditOn) {
    }
}
