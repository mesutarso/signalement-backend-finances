package cd.gouv.finances.signalements.report.payment;

import cd.gouv.finances.signalements.araka.ArakaProperties;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.query.ReadAdministrationReports.Page;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('payment.read')")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReadPaymentJournal {
    private static final int SIZE = 20;
    private static final Set<String> STATES = Set.of("paid", "failed", "pending", "unknown");
    private static final String SOURCE = """
            with attempts as (
                select t.id, l.prime_id, l.statement_id, s.reference_code as statement_reference,
                    t.reference, t.transaction_id, t.provider, t.amount, t.state, t.code,
                    t.started_at, t.updated_at,
                    t.state='failed' and not l.released and not s.exceptional and s.status='authorized' as retryable,
                    row_number() over (partition by l.prime_id order by t.started_at desc, t.id desc) as attempt_number
                from app.payment_transfer t
                join app.payment_statement_line l on l.id=t.line_id
                join app.payment_statement s on s.id=l.statement_id
                where t.environment=?
            )
            """;

    private final JdbcTemplate jdbc;
    private final ArakaProperties properties;

    public record Counts(long paid, long failed, long pending, long unknown) { }
    public record Entry(UUID id, UUID rewardId, UUID statementId, String statementReference, String reference,
                        String transactionId, String provider, BigDecimal amount, String state, String code,
                        Instant startedAt, Instant updatedAt, boolean retryable) { }
    public record Journal(String environment, String activeEnvironment, boolean configured, Counts counts, Page<Entry> entries) { }

    public Journal read(String requestedEnvironment, String status, int requestedPage) {
        var environment = environment(requestedEnvironment);
        if (status != null && !STATES.contains(status)) throw new ReportRejected("validation", 400);
        var counts = jdbc.queryForObject(SOURCE + """
                select count(*) filter (where state='paid') as paid, count(*) filter (where state='failed') as failed,
                    count(*) filter (where state in ('pending','submitting')) as pending,
                    count(*) filter (where state='unknown') as unknown
                from attempts where attempt_number=1
                """, (row, index) -> new Counts(row.getLong("paid"), row.getLong("failed"), row.getLong("pending"),
                        row.getLong("unknown")), environment);
        var total = switch (status == null ? "" : status) {
            case "paid" -> counts.paid();
            case "failed" -> counts.failed();
            case "pending" -> counts.pending();
            case "unknown" -> counts.unknown();
            default -> counts.paid() + counts.failed() + counts.pending() + counts.unknown();
        };
        var page = (int) Math.max(0, Math.min(requestedPage, Math.max(0, total - 1) / SIZE));
        var entries = jdbc.query(SOURCE + """
                select * from attempts where attempt_number=1
                    and (? = '' or case when state='submitting' then 'pending' else state end = ?)
                order by started_at desc, id desc limit ? offset ?
                """, ReadPaymentJournal::entry, environment, status == null ? "" : status, status, SIZE, (long) page * SIZE);
        return new Journal(environment, environment(null), properties.configured(), counts, new Page<>(entries, page, SIZE, total));
    }

    public List<Entry> history(String environment, UUID rewardId) {
        return jdbc.query(SOURCE + "select * from attempts where prime_id=? order by started_at desc, id desc",
                ReadPaymentJournal::entry, environment(environment), rewardId);
    }

    private String environment(String requested) {
        if (requested == null) return properties.sandbox() ? "sandbox" : "production";
        if (!Set.of("sandbox", "production").contains(requested)) throw new ReportRejected("payment-environment", 400);
        return requested;
    }

    private static Entry entry(ResultSet row, int index) throws SQLException {
        return new Entry(row.getObject("id", UUID.class), row.getObject("prime_id", UUID.class),
                row.getObject("statement_id", UUID.class), row.getString("statement_reference"), row.getString("reference"),
                row.getString("transaction_id"), row.getString("provider"), row.getBigDecimal("amount"), row.getString("state"),
                row.getString("code"), row.getTimestamp("started_at").toInstant(), row.getTimestamp("updated_at").toInstant(),
                row.getBoolean("retryable"));
    }
}
