package cd.gouv.finances.signalements.report.declarant;

import cd.gouv.finances.signalements.report.ReportReference;
import cd.gouv.finances.signalements.report.dto.DeclarantSituationResponse;
import cd.gouv.finances.signalements.report.rule.ActiveRule;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadDeclarantSituationQuery {

    private static final String READERS = "hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')";

    private final JdbcTemplate jdbc;
    private final ReportReference reportReference;
    private final ActiveRule activeRule;

    public ReadDeclarantSituationQuery(JdbcTemplate jdbc, ReportReference reportReference, ActiveRule activeRule) {
        this.jdbc = jdbc;
        this.reportReference = reportReference;
        this.activeRule = activeRule;
    }

    @PreAuthorize(READERS)
    @Transactional(readOnly = true)
    public DeclarantSituationResponse execute(String reference) {
        var match = reportReference.require(reference);
        var declarant = jdbc.queryForObject("select nullif(btrim(reporter_user_id), '') from app.report where id = ?", String.class, match.id());
        var reports = reports(match.id(), declarant);
        var rule = activeRule.requireActive();
        var primeAmount = declarant == null ? 0 : sum("""
                select coalesce(sum(amount), 0) from app.prime where declarant_id = ? and report_id is not null and status <> 'withdrawn'
                """, declarant);
        var paidAmount = declarant == null ? 0 : sum("""
                select coalesce(sum(line.amount), 0)
                from app.payment_attempt attempt
                join app.payment_statement_line line on line.id = attempt.line_id
                join app.prime prime on prime.id = line.prime_id
                where attempt.result = 'paid' and prime.report_id is not null and prime.declarant_id = ?
                """, declarant);
        var approved = declarant != null && count("""
                select count(*) from app.prime where declarant_id = ? and report_id is not null and status = 'approved'
                """, declarant) > 0;
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
                paidAmount,
                rule.ceiling(),
                Math.max(rule.ceiling() - primeAmount, 0),
                tickets,
                lots,
                approved,
                paidAmount > 0,
                reports);
    }

    private java.util.List<DeclarantSituationResponse.Report> reports(UUID reportId, String declarant) {
        var sql = """
                select report.id, report.reference_code, report.state, latest.decision
                from app.report report
                left join lateral (
                    select decision from app.qualification
                    where report_id = report.id
                    order by recorded_at desc, id desc
                    limit 1
                ) latest on true
                where %s
                order by report.reported_at desc, report.reference_code
                limit 20
                """.formatted(declarant == null ? "report.id = ?" : "btrim(report.reporter_user_id) = ?");
        return jdbc.query(sql, (row, number) -> new DeclarantSituationResponse.Report(
                row.getObject(1, UUID.class),
                row.getString(2),
                DeclarantStatus.of(row.getString(3), row.getString(4))), declarant == null ? reportId : declarant);
    }

    private int sum(String sql, Object... arguments) {
        var value = jdbc.queryForObject(sql, Long.class, arguments);
        return value == null ? 0 : Math.toIntExact(value);
    }

    private int count(String sql, Object argument) {
        var value = jdbc.queryForObject(sql, Long.class, argument);
        return value == null ? 0 : Math.toIntExact(value);
    }
}
