package cd.gouv.finances.signalements.report.payment;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.dto.PaymentAttemptResponse;
import cd.gouv.finances.signalements.report.dto.PaymentExclusionResponse;
import cd.gouv.finances.signalements.report.dto.PaymentLineResponse;
import cd.gouv.finances.signalements.report.dto.PaymentPageResponse;
import cd.gouv.finances.signalements.report.dto.PaymentStatementResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadPaymentStatementsQuery {

    private static final int SIZE = 20;
    private static final String READERS = "hasAnyRole('SUPERVISOR','AUDITOR','ADMINISTRATOR','CELLULE','CABINET','PAYMASTER')";

    private final JdbcTemplate jdbc;

    public ReadPaymentStatementsQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PreAuthorize(READERS)
    @Transactional(readOnly = true)
    public PaymentPageResponse execute(int page) {
        var requested = Math.max(page, 0);
        var total = jdbc.queryForObject("select count(*) from app.payment_statement", Long.class);
        var items = jdbc.query("""
                select statement.id, statement.reference_code, statement.status,
                       coalesce(sum(line.amount), 0), statement.prepared_at, statement.due_on
                from app.payment_statement statement
                left join app.payment_statement_line line on line.statement_id = statement.id
                group by statement.id
                order by statement.prepared_at desc
                limit ? offset ?
                """, (row, number) -> new PaymentPageResponse.Item(
                row.getObject(1, UUID.class),
                row.getString(2),
                row.getString(3),
                row.getInt(4),
                row.getTimestamp(5).toInstant(),
                row.getDate(6) == null ? null : row.getDate(6).toLocalDate()), SIZE, requested * (long) SIZE);
        return new PaymentPageResponse(items, requested, SIZE, total == null ? 0 : total);
    }

    @PreAuthorize(READERS)
    @Transactional(readOnly = true)
    public PaymentStatementResponse executeOne(UUID id) {
        var rows = jdbc.query("""
                select id, reference_code, status, prepared_at, controlled_at, visaed_at, authorized_at, due_on, reason
                from app.payment_statement
                where id = ?
                """, (row, number) -> new Header(
                row.getObject(1, UUID.class),
                row.getString(2),
                row.getString(3),
                row.getTimestamp(4).toInstant(),
                row.getTimestamp(5) == null ? null : row.getTimestamp(5).toInstant(),
                row.getTimestamp(6) == null ? null : row.getTimestamp(6).toInstant(),
                row.getTimestamp(7) == null ? null : row.getTimestamp(7).toInstant(),
                row.getDate(8) == null ? null : row.getDate(8).toLocalDate(),
                row.getString(9)), id);
        if (rows.isEmpty()) {
            throw new ReportRejected("payment-absent", 404);
        }
        var row = rows.getFirst();
        var attempts = new HashMap<UUID, List<PaymentAttemptResponse>>();
        jdbc.query("""
                select id, line_id, result, reference_code, reason, recorded_at, operator
                from app.payment_attempt
                where statement_id = ? and reference_code not like 'SIM-%'
                order by recorded_at, id
                """, (attempt, number) -> {
            attempts.computeIfAbsent(attempt.getObject(2, UUID.class), key -> new ArrayList<>()).add(new PaymentAttemptResponse(
                    attempt.getObject(1, UUID.class),
                    attempt.getString(3),
                    attempt.getString(4),
                    attempt.getString(5),
                    attempt.getTimestamp(6).toInstant(),
                    attempt.getString(7)));
            return null;
        }, id);
        var lines = jdbc.query("""
                select id, prime_id, threshold, amount, phone
                from app.payment_statement_line
                where statement_id = ?
                order by threshold, id
                """, (line, number) -> {
            var lineId = line.getObject(1, UUID.class);
            return new PaymentLineResponse(
                    lineId,
                    line.getObject(2, UUID.class),
                    line.getInt(3),
                    line.getInt(4),
                    line.getString(5),
                    attempts.getOrDefault(lineId, List.of()));
        }, id);
        var exclusions = jdbc.query("""
                select prime_id, reason
                from app.payment_statement_exclusion
                where statement_id = ?
                order by prime_id
                """, (exclusion, number) -> new PaymentExclusionResponse(exclusion.getObject(1, UUID.class), exclusion.getString(2)), id);
        var total = lines.stream().mapToInt(PaymentLineResponse::amount).sum();
        return new PaymentStatementResponse(
                row.id(),
                row.reference(),
                row.status(),
                total,
                row.preparedAt(),
                row.controlledAt(),
                row.visaedAt(),
                row.authorizedAt(),
                row.dueOn(),
                row.reason(),
                lines,
                exclusions);
    }

    private record Header(
            UUID id,
            String reference,
            String status,
            Instant preparedAt,
            Instant controlledAt,
            Instant visaedAt,
            Instant authorizedAt,
            LocalDate dueOn,
            String reason) {
    }
}
