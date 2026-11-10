package cd.gouv.finances.signalements.report.rule;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.points.PointsAward;
import cd.gouv.finances.signalements.report.prime.PrimeScale;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ActiveRule {

    private final JdbcTemplate jdbc;

    public ActiveRule(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Snapshot requireActive() {
        var rows = jdbc.query("""
                select id, code, fake_invoice, missing_transmission, understated, non_normalized, refusal,
                       bonus, ceiling, daily_cap, spacing_days
                from app.rule_version
                where active = true
                """, (row, number) -> row(row));
        if (rows.size() != 1) {
            throw new ReportRejected("rule-absent", 409);
        }
        return withTiers(rows.getFirst());
    }

    public Snapshot requireForReport(UUID reportId) {
        var codes = jdbc.queryForList("""
                select coalesce(
                    (select movement.rule_version from app.point_movement movement
                     where movement.report_id = report.id and movement.kind = 'credit'
                     order by movement.recorded_at, movement.id limit 1),
                    (select activation.rule_code from app.rule_activation activation
                     where activation.activated_at <= report.reported_at at time zone 'Africa/Kinshasa'
                     order by activation.activated_at desc, activation.id desc limit 1),
                    'cdc-2026-10-28')
                from app.report report where report.id = ?
                """, String.class, reportId);
        if (codes.isEmpty()) throw new ReportRejected("report-absent", 404);
        return require(codes.getFirst());
    }

    public Snapshot require(String code) {
        var rows = jdbc.query("""
                select id, code, fake_invoice, missing_transmission, understated, non_normalized, refusal,
                       bonus, ceiling, daily_cap, spacing_days
                from app.rule_version
                where code = ?
                """, (row, number) -> row(row), code);
        if (rows.isEmpty()) {
            throw new ReportRejected("rule-absent", 404);
        }
        return withTiers(rows.getFirst());
    }

    public static Snapshot cdc() {
        return new Snapshot(null, PointsAward.RULE, PointsAward.CDC, PrimeScale.CEILING, PrimeScale.cdcTiers(), true);
    }

    private Snapshot withTiers(Snapshot snapshot) {
        var tiers = jdbc.query("""
                select threshold, amount
                from app.rule_tier
                where rule_id = ?
                order by position
                """, (row, number) -> new PrimeScale.Tier(row.getInt(1), row.getInt(2)), snapshot.id());
        return new Snapshot(snapshot.id(), snapshot.code(), snapshot.scale(), snapshot.ceiling(), tiers, snapshot.active());
    }

    private static Snapshot row(java.sql.ResultSet row) throws java.sql.SQLException {
        return new Snapshot(
                row.getObject(1, UUID.class),
                row.getString(2),
                new PointsAward.Scale(row.getInt(3), row.getInt(4), row.getInt(5), row.getInt(6), row.getInt(7), row.getInt(8), row.getInt(10), row.getInt(11)),
                row.getInt(9),
                List.of(),
                true);
    }

    public record Snapshot(
            UUID id,
            String code,
            PointsAward.Scale scale,
            int ceiling,
            List<PrimeScale.Tier> tiers,
            boolean active) {
    }
}
