package cd.gouv.finances.signalements.report.indicator;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.dto.IndicatorResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadIndicatorsQuery {

    private static final ZoneId KINSHASA = ZoneId.of("Africa/Kinshasa");
    private static final Set<String> FINDINGS = Set.of(
            "fake-invoice", "missing-transmission", "understated", "non-normalized", "refusal", "unknown-seller", "nonconform", "vat-unregistered");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ReadIndicatorsQuery(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public IndicatorResponse execute(LocalDate from, LocalDate to, String finding) {
        if ((from == null) != (to == null) || (from != null && to.isBefore(from))) {
            throw new ReportRejected("indicator-period", 400);
        }
        var keptFinding = finding == null || finding.isBlank() ? null : finding.trim();
        if (keptFinding != null && !FINDINGS.contains(keptFinding)) {
            throw new ReportRejected("indicator-finding", 400);
        }
        var today = LocalDate.now(clock.withZone(KINSHASA));
        var weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new IndicatorResponse(
                from,
                to,
                keptFinding,
                figures(from, to, keptFinding),
                figures(weekStart, weekStart.plusDays(6), keptFinding),
                figures(today.withDayOfMonth(1), today.with(TemporalAdjusters.lastDayOfMonth()), keptFinding),
                clock.instant());
    }

    private IndicatorResponse.Figures figures(LocalDate from, LocalDate to, String finding) {
        return new IndicatorResponse.Figures(
                from,
                to,
                received(from, to),
                treated(from, to),
                findings(from, to, finding),
                dated("select count(*) from app.letter_dispatch where true", "sent_on", from, to),
                dated("select count(*) from app.letter_response where true", "received_on", from, to),
                dated("select count(*) from app.prime where report_id is not null", "recorded_at", from, to),
                paidAmount(from, to),
                dated("select count(*) from app.payment_attempt where result = 'paid'", "recorded_at", from, to),
                dated("select count(*) from app.campaign_draw where true", "drawn_at", from, to),
                dated("select count(*) from app.abuse_note where true", "recorded_at", from, to));
    }

    private long received(LocalDate from, LocalDate to) {
        if (from == null) {
            return count("select count(*) from app.report");
        }
        return count("select count(*) from app.report where reported_on >= ? and reported_on <= ?", from, to);
    }

    private long treated(LocalDate from, LocalDate to) {
        var sql = """
                select count(*) from app.report report
                join lateral (
                    select decision, recorded_at from app.qualification
                    where report_id = report.id
                    order by recorded_at desc, id desc
                    limit 1
                ) latest on latest.decision in ('confirm', 'reject')
                """;
        if (from == null) {
            return count(sql);
        }
        return count(sql + " where latest.recorded_at >= ? and latest.recorded_at < ?", Timestamp.from(start(from)), Timestamp.from(end(to)));
    }

    private long findings(LocalDate from, LocalDate to, String finding) {
        var sql = """
                select count(*) from app.report report
                join lateral (
                    select decision, finding, recorded_at from app.qualification
                    where report_id = report.id
                    order by recorded_at desc, id desc
                    limit 1
                ) latest on latest.decision = 'confirm'
                """;
        if (finding != null) {
            sql += " and latest.finding = ?";
        }
        if (from == null && finding == null) {
            return count(sql);
        }
        if (from == null) {
            return count(sql, finding);
        }
        sql += " and latest.recorded_at >= ? and latest.recorded_at < ?";
        if (finding == null) {
            return count(sql, Timestamp.from(start(from)), Timestamp.from(end(to)));
        }
        return count(sql, finding, Timestamp.from(start(from)), Timestamp.from(end(to)));
    }

    private long paidAmount(LocalDate from, LocalDate to) {
        var sql = """
                select coalesce(sum(line.amount), 0)
                from app.payment_attempt attempt
                join app.payment_statement_line line on line.id = attempt.line_id
                join app.prime reward on reward.id = line.prime_id and reward.report_id is not null
                where attempt.result = 'paid'
                """;
        if (from == null) {
            return count(sql);
        }
        return count(sql + " and attempt.recorded_at >= ? and attempt.recorded_at < ?", Timestamp.from(start(from)), Timestamp.from(end(to)));
    }

    private long dated(String sql, String column, LocalDate from, LocalDate to) {
        if (from == null) {
            return count(sql);
        }
        var dateColumn = "received_on".equals(column) || "sent_on".equals(column);
        if (dateColumn) {
            return count(sql + " and " + column + " >= ? and " + column + " <= ?", from, to);
        }
        return count(sql + " and " + column + " >= ? and " + column + " < ?", Timestamp.from(start(from)), Timestamp.from(end(to)));
    }

    private long count(String sql, Object... arguments) {
        var value = jdbc.queryForObject(sql, Long.class, arguments);
        return value == null ? 0 : value;
    }

    private java.time.Instant start(LocalDate day) {
        return day.atStartOfDay(KINSHASA).toInstant();
    }

    private java.time.Instant end(LocalDate day) {
        return day.plusDays(1).atStartOfDay(KINSHASA).toInstant();
    }
}
