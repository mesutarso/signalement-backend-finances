package cd.gouv.finances.signalements.report.points;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.points.PointsAward.Claim;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PointsContext {

    private final JdbcTemplate jdbc;

    public PointsContext(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Claim claim(UUID reportId) {
        var rows = jdbc.query("""
                select report.reference_code, report.reported_on, report.reported_at, report.reporter_user_id
                from app.report report
                where report.id = ?
                """, (row, number) -> new ReportRow(
                row.getDate(2).toLocalDate(),
                row.getTimestamp(3).toLocalDateTime(),
                row.getString(4)), reportId);
        if (rows.isEmpty()) {
            throw new ReportRejected("report-absent", 404);
        }
        var report = rows.getFirst();
        var qualifications = jdbc.query("""
                select decision, finding, seller_tax_id
                from app.qualification
                where report_id = ?
                order by recorded_at desc
                limit 1
                """, (row, number) -> new QualificationRow(row.getString(1), row.getString(2), row.getString(3)), reportId);
        var qualification = qualifications.isEmpty() ? new QualificationRow(null, null, null) : qualifications.getFirst();
        var taxId = qualification.taxId() == null ? "" : qualification.taxId().trim();
        var declarantId = report.declarantId() == null ? "" : report.declarantId().trim();
        return new Claim(
                qualification.decision(),
                qualification.finding(),
                taxId,
                declarantId,
                losingDuplicate(reportId),
                earlierSameDay(reportId, declarantId, report.filedOn(), report.reportedAt()),
                report.filedOn(),
                creditedDates(taxId),
                establishmentAlreadyReported(reportId, taxId, report.reportedAt()) || bonusAlreadyGranted(taxId),
                net(reportId));
    }

    public int net(UUID reportId) {
        var net = jdbc.queryForObject(
                "select coalesce(sum(base_points + bonus_points), 0) from app.point_movement where report_id = ?",
                Integer.class,
                reportId);
        return net == null ? 0 : net;
    }

    public Balance balance(UUID reportId) {
        var rows = jdbc.query("""
                select coalesce(sum(base_points), 0), coalesce(sum(bonus_points), 0),
                       max(declarant_id), max(tax_id), max(finding), max(filed_on),
                       (select movement.rule_version
                        from app.point_movement movement
                        where movement.report_id = ? and movement.kind = 'credit'
                        order by movement.recorded_at desc
                        limit 1)
                from app.point_movement
                where report_id = ?
                """, (row, number) -> new Balance(
                row.getInt(1),
                row.getInt(2),
                row.getString(3),
                row.getString(4),
                row.getString(5),
                row.getDate(6) == null ? null : row.getDate(6).toLocalDate(),
                row.getString(7)), reportId, reportId);
        return rows.getFirst();
    }

    private boolean losingDuplicate(UUID reportId) {
        var losing = jdbc.queryForObject("""
                select exists (
                    select 1 from app.duplicate_alert
                    where status = 'confirmed'
                      and priority_report_id is not null
                      and priority_report_id <> ?
                      and (report_id = ? or other_report_id = ?)
                )
                """, Boolean.class, reportId, reportId, reportId);
        return Boolean.TRUE.equals(losing);
    }

    private int earlierSameDay(UUID reportId, String declarantId, LocalDate filedOn, LocalDateTime reportedAt) {
        if (declarantId.isBlank()) {
            return 0;
        }
        var filedAt = Timestamp.valueOf(reportedAt);
        var count = jdbc.queryForObject("""
                select count(*) from app.report other
                where other.reporter_user_id = ?
                  and other.reported_on = ?
                  and other.id <> ?
                  and (
                    other.reported_at < ?
                    or (other.reported_at = ? and other.reference_code < (select reference_code from app.report where id = ?))
                    or (other.reported_at = ? and other.reference_code = (select reference_code from app.report where id = ?) and other.id < ?)
                  )
                  and not exists (
                    select 1 from app.duplicate_alert alert
                    where alert.status = 'confirmed'
                      and alert.priority_report_id is not null
                      and alert.priority_report_id <> other.id
                      and (alert.report_id = other.id or alert.other_report_id = other.id)
                  )
                """, Integer.class, declarantId, filedOn, reportId, filedAt, filedAt, reportId, filedAt, reportId, reportId);
        return count == null ? 0 : count;
    }

    private java.util.List<LocalDate> creditedDates(String taxId) {
        if (taxId.isBlank()) {
            return java.util.List.of();
        }
        return jdbc.query("""
                select filed_on
                from app.point_movement
                where lower(trim(tax_id)) = lower(trim(?))
                group by report_id, filed_on
                having sum(base_points + bonus_points) > 0
                """, (row, number) -> row.getDate(1).toLocalDate(), taxId);
    }

    private boolean establishmentAlreadyReported(UUID reportId, String taxId, LocalDateTime reportedAt) {
        if (taxId.isBlank()) {
            return false;
        }
        var filedAt = Timestamp.valueOf(reportedAt);
        var reported = jdbc.queryForObject("""
                select exists (
                    select 1
                    from app.qualification qualification
                    join app.report report on report.id = qualification.report_id
                    where qualification.report_id <> ?
                      and lower(trim(qualification.seller_tax_id)) = lower(trim(?))
                      and (
                        report.reported_at < ?
                        or (report.reported_at = ? and report.reference_code < (select reference_code from app.report where id = ?))
                        or (report.reported_at = ? and report.reference_code = (select reference_code from app.report where id = ?) and report.id < ?)
                      )
                )
                """, Boolean.class, reportId, taxId, filedAt, filedAt, reportId, filedAt, reportId, reportId);
        return Boolean.TRUE.equals(reported);
    }

    private boolean bonusAlreadyGranted(String taxId) {
        if (taxId.isBlank()) {
            return false;
        }
        var granted = jdbc.queryForObject("""
                select exists (
                    select 1 from app.point_movement
                    where lower(trim(tax_id)) = lower(trim(?)) and bonus_points > 0
                )
                """, Boolean.class, taxId);
        return Boolean.TRUE.equals(granted);
    }

    public record Balance(int base, int bonus, String declarantId, String taxId, String finding, LocalDate filedOn, String ruleVersion) {
    }

    private record ReportRow(LocalDate filedOn, LocalDateTime reportedAt, String declarantId) {
    }

    private record QualificationRow(String decision, String finding, String taxId) {
    }
}
