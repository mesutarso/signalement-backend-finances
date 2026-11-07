package cd.gouv.finances.signalements.report.prime;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PrimeContext {

    public static final String READY = "ready";
    public static final String MISSING = "missing";
    public static final String SEVERAL = "several";
    public static final String UNRECORDED = "unrecorded";

    private final JdbcTemplate jdbc;

    public PrimeContext(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Phone(String number, String state) {
    }

    public int netPoints(String declarantId, String ruleVersion) {
        var net = jdbc.queryForObject("""
                select coalesce(sum(base_points + bonus_points), 0)
                from app.point_movement
                where declarant_id = ? and rule_version = ?
                """, Integer.class, declarantId, ruleVersion);
        return net == null ? 0 : net;
    }

    public Phone phone(String declarantId, String ruleVersion) {
        var numbers = jdbc.queryForList("""
                select distinct btrim(report.reporter_phone)
                from app.point_movement movement
                join app.report report on report.id = movement.report_id
                where movement.declarant_id = ?
                  and movement.rule_version = ?
                  and report.reporter_phone is not null
                  and btrim(report.reporter_phone) <> ''
                """, String.class, declarantId, ruleVersion);
        if (numbers.isEmpty()) {
            return new Phone(null, MISSING);
        }
        if (numbers.size() > 1) {
            return new Phone(null, SEVERAL);
        }
        return new Phone(numbers.get(0), READY);
    }

    public List<UUID> findingActors(String declarantId, String ruleVersion) {
        return jdbc.query("""
                select distinct qualification.actor_id
                from app.qualification qualification
                join app.point_movement movement on movement.report_id = qualification.report_id
                where movement.declarant_id = ?
                  and movement.rule_version = ?
                  and qualification.decision = 'confirm'
                  and qualification.recorded_at = (
                      select max(latest.recorded_at) from app.qualification latest where latest.report_id = qualification.report_id
                  )
                """, (row, number) -> row.getObject(1, UUID.class), declarantId, ruleVersion);
    }

    public String declarantId(java.util.UUID reportId) {
        var ids = jdbc.query("""
                select reporter_user_id
                from app.report
                where id = ?
                """, (row, number) -> row.getString(1), reportId);
        if (ids.isEmpty() || ids.get(0) == null || ids.get(0).isBlank()) {
            return "";
        }
        return ids.get(0).trim();
    }
}
