package cd.gouv.finances.signalements.report.declarant;

import cd.gouv.finances.signalements.report.ReportReference;
import cd.gouv.finances.signalements.report.dto.DeclarantSituationResponse;
import cd.gouv.finances.signalements.report.query.ReadAdministrationReports;
import cd.gouv.finances.signalements.report.rule.ActiveRule;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadDeclarantSituationQuery {

    private static final String READERS = "hasAuthority('declarant.read')";
    private static final int PAGE_SIZE = 20;

    private final JdbcTemplate jdbc;
    private final ReportReference reportReference;
    private final ActiveRule activeRule;

    public ReadDeclarantSituationQuery(JdbcTemplate jdbc, ReportReference reportReference, ActiveRule activeRule) {
        this.jdbc = jdbc;
        this.reportReference = reportReference;
        this.activeRule = activeRule;
    }

    @PreAuthorize(READERS)
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DeclarantSituationResponse execute(String reference, int requestedPage, boolean showIdentity) {
        var match = reportReference.require(reference);
        var declarant = jdbc.queryForObject("select nullif(btrim(reporter_user_id), '') from app.report where id = ?", String.class, match.id());
        var filter = declarant == null ? "report.id = ?" : "btrim(report.reporter_user_id) = ?";
        var scope = declarant == null ? match.id() : declarant;
        var totals = validationTotals(filter, scope);
        var page = (int) Math.max(0, Math.min(requestedPage, Math.max(0, totals.total() - 1) / PAGE_SIZE));
        var reports = reports(filter, scope, page);
        var identity = showIdentity ? identity(filter, scope) : null;
        var rule = activeRule.requireActive();
        var rewardFilter = declarant == null ? "prime.report_id = ?" : "prime.report_id is not null and prime.declarant_id = ?";
        var primeAmount = sum("""
                select coalesce(sum(amount), 0) from app.prime prime where %s and status <> 'withdrawn'
                """.formatted(rewardFilter), scope);
        var paidAmount = sum("""
                select coalesce(sum(line.amount), 0)
                from app.payment_attempt attempt
                join app.payment_statement_line line on line.id = attempt.line_id
                join app.prime prime on prime.id = line.prime_id
                where attempt.result = 'paid' and attempt.reference_code not like 'SIM-%%' and %s
                """.formatted(rewardFilter), scope);
        var approved = count("""
                select count(*) from app.prime prime where %s and status = 'approved'
                """.formatted(rewardFilter), scope) > 0;
        var tickets = count(declarant == null
                ? "select count(*) from app.draw_ticket where report_id = ? and excluded_at is null"
                : """
                select count(*) from app.draw_ticket ticket
                join app.report report on report.id = ticket.report_id
                where ticket.excluded_at is null and btrim(report.reporter_user_id) = ?
                """, declarant == null ? match.id() : declarant);
        var lots = count(declarant == null
                ? """
                select count(*) from app.draw_prize prize
                join app.draw_ticket ticket on ticket.id = prize.ticket_id
                where ticket.report_id = ?
                """
                : """
                select count(*) from app.draw_prize prize
                join app.draw_ticket ticket on ticket.id = prize.ticket_id
                join app.report report on report.id = ticket.report_id
                where btrim(report.reporter_user_id) = ?
                """, declarant == null ? match.id() : declarant);
        return new DeclarantSituationResponse(
                match.reference(),
                primeAmount,
                totals.validated(),
                totals.rewardAmount(),
                paidAmount,
                rule.ceiling(),
                Math.max(rule.ceiling() - primeAmount, 0),
                tickets,
                lots,
                approved,
                paidAmount > 0,
                declarant != null,
                identity,
                totals.rejected(),
                totals.duplicates(),
                totals.pending(),
                totals.total(),
                page,
                PAGE_SIZE,
                reports);
    }

    private java.util.List<DeclarantSituationResponse.Report> reports(String filter, Object scope, int page) {
        var sql = ReadAdministrationReports.REPORTS_WITH_OUTCOME + """
                select report.id, report.reference_code, report.state, report.validation_state,
                    report.outcome, report.title, report.reported_at, report.historical_decision
                from reports report
                where %s
                order by report.reported_at desc nulls last, report.reference_code, report.id
                limit ? offset ?
                """.formatted(filter);
        return jdbc.query(sql, (row, number) -> new DeclarantSituationResponse.Report(
                row.getObject(1, UUID.class),
                row.getString(2),
                switch (row.getString(5)) {
                    case "validated" -> DeclarantStatus.CONFIRMED;
                    case "duplicate" -> "duplicate";
                    case "rejected" -> DeclarantStatus.REJECTED;
                    default -> DeclarantStatus.of(row.getString(3), row.getString(4), row.getString(8));
                },
                row.getString(6),
                row.getObject(7, LocalDateTime.class)), scope, PAGE_SIZE, page * (long) PAGE_SIZE);
    }

    private ValidationTotals validationTotals(String filter, Object scope) {
        return jdbc.queryForObject(ReadAdministrationReports.REPORTS_WITH_OUTCOME + """
                select count(*) as total, count(*) filter (where outcome = 'validated') as validated_count,
                    count(*) filter (where outcome = 'rejected') as rejected_count,
                    count(*) filter (where outcome = 'duplicate') as duplicate_count,
                    count(*) filter (where outcome = 'pending') as pending_count,
                    coalesce(sum(reward.amount) filter (where outcome = 'validated'), 0) as reward_amount
                from reports report
                left join app.prime reward on reward.report_id = report.id and reward.status <> 'withdrawn'
                where %s
                """.formatted(filter),
                (row, number) -> new ValidationTotals(row.getLong("total"), row.getLong("validated_count"),
                        row.getLong("rejected_count"), row.getLong("duplicate_count"), row.getLong("pending_count"),
                        row.getLong("reward_amount")), scope);
    }

    private DeclarantSituationResponse.Identity identity(String filter, Object scope) {
        return jdbc.queryForObject("""
                select
                    (array_agg(nullif(btrim(reporter_name), '') order by reported_at desc nulls last, id desc)
                        filter (where nullif(btrim(reporter_name), '') is not null))[1] as name,
                    (array_agg(nullif(btrim(reporter_email), '') order by reported_at desc nulls last, id desc)
                        filter (where nullif(btrim(reporter_email), '') is not null))[1] as email,
                    (array_agg(nullif(btrim(reporter_phone), '') order by reported_at desc nulls last, id desc)
                        filter (where nullif(btrim(reporter_phone), '') is not null))[1] as phone
                from app.report report where %s
                """.formatted(filter), (row, number) -> new DeclarantSituationResponse.Identity(
                        row.getString("name"), row.getString("email"), row.getString("phone")), scope);
    }

    private record ValidationTotals(long total, long validated, long rejected, long duplicates, long pending,
                                    long rewardAmount) { }

    private int sum(String sql, Object... arguments) {
        var value = jdbc.queryForObject(sql, Long.class, arguments);
        return value == null ? 0 : Math.toIntExact(value);
    }

    private int count(String sql, Object argument) {
        var value = jdbc.queryForObject(sql, Long.class, argument);
        return value == null ? 0 : Math.toIntExact(value);
    }
}
