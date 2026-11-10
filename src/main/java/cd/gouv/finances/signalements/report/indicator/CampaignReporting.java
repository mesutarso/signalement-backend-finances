package cd.gouv.finances.signalements.report.indicator;

import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.dto.IndicatorResponse;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class CampaignReporting {
    private final JdbcTemplate jdbc;
    private final ReadIndicatorsQuery indicators;
    private final ObjectMapper mapper;
    private final RecordAudit audit;
    private final Clock clock;

    public record Budget(Long amount, long approvedAmount, long paidAmount) { }
    public record Report(UUID id, String kind, LocalDate from, LocalDate to, IndicatorResponse.Figures figures, Long budget, Instant generatedAt) { }

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public Budget budget() {
        var amounts = jdbc.queryForList("select amount from app.campaign_budget order by recorded_at desc, id desc limit 1", Long.class);
        var approved = jdbc.queryForObject("select coalesce(sum(amount), 0) from app.prime where status = 'approved'", Long.class);
        var paid = jdbc.queryForObject("""
                select coalesce(sum(line.amount), 0) from app.payment_statement_line line where exists (
                    select 1 from app.payment_attempt attempt where attempt.line_id = line.id and attempt.result = 'paid')
                """, Long.class);
        return new Budget(amounts.isEmpty() ? null : amounts.getFirst(), approved == null ? 0 : approved, paid == null ? 0 : paid);
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public void recordBudget(long amount, String reference, UUID actor) {
        var kept = reference == null ? "" : reference.trim();
        if (amount <= 0 || amount > 1_000_000_000_000L || kept.isEmpty() || kept.length() > 160) throw new ReportRejected("campaign-budget", 400);
        var id = UUID.randomUUID();
        jdbc.update("insert into app.campaign_budget (id, amount, reference, actor_id, recorded_at) values (?, ?, ?, ?, ?)",
                id, amount, kept, actor, java.sql.Timestamp.from(clock.instant()));
        audit.allowed(actor, "campaign-budget", "budget", id.toString());
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public void capture(String kind, UUID actor) {
        if (!Set.of("weekly", "monthly").contains(kind)) throw new ReportRejected("campaign-report", 400);
        var today = LocalDate.now(clock.withZone(ZoneId.of("Africa/Kinshasa")));
        var from = "weekly".equals(kind) ? today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
                : today.withDayOfMonth(1).minusMonths(1);
        var to = "weekly".equals(kind) ? from.plusDays(6) : from.with(TemporalAdjusters.lastDayOfMonth());
        var figures = indicators.execute(from, to, null).figures();
        var id = UUID.randomUUID();
        var created = jdbc.update("""
                insert into app.campaign_report (id, kind, period_start, period_end, figures, budget, actor_id, generated_at)
                values (?, ?, ?, ?, ?::jsonb, ?, ?, ?) on conflict (kind, period_start) do nothing
                """, id, kind, from, to, mapper.writeValueAsString(figures), budget().amount(), actor, java.sql.Timestamp.from(clock.instant()));
        if (created > 0) audit.allowed(actor, "campaign-report", "report", id.toString());
    }

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public List<Report> reports() {
        return jdbc.query("select id, kind, period_start, period_end, figures::text, budget, generated_at from app.campaign_report order by generated_at desc limit 20",
                (row, number) -> new Report(row.getObject(1, UUID.class), row.getString(2), row.getDate(3).toLocalDate(), row.getDate(4).toLocalDate(),
                        mapper.readValue(row.getString(5), IndicatorResponse.Figures.class), row.getObject(6, Long.class), row.getTimestamp(7).toInstant()));
    }
}
