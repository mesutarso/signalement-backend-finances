package cd.gouv.finances.signalements.report.sample;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.dto.PaymentSamplePageResponse;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadPaymentSamplesQuery {

    private static final int SIZE = 20;
    private static final String READERS = "hasAnyRole('SUPERVISOR','AUDITOR','ADMINISTRATOR')";

    private final JdbcTemplate jdbc;

    public ReadPaymentSamplesQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PreAuthorize(READERS)
    @Transactional(readOnly = true)
    public PaymentSamplePageResponse execute(int page) {
        var requested = Math.max(page, 0);
        var total = jdbc.queryForObject("select count(*) from app.payment_sample", Long.class);
        var items = jdbc.query("""
                select sample.id, sample.reference_code, sample.period_start, sample.period_end, count(line.id), sample.prepared_at
                from app.payment_sample sample
                left join app.payment_sample_line line on line.sample_id = sample.id
                group by sample.id
                order by sample.prepared_at desc
                limit ? offset ?
                """, (row, number) -> new PaymentSamplePageResponse.Item(
                row.getObject(1, UUID.class),
                row.getString(2),
                row.getDate(3).toLocalDate(),
                row.getDate(4).toLocalDate(),
                row.getInt(5),
                row.getTimestamp(6).toInstant()), SIZE, requested * (long) SIZE);
        return new PaymentSamplePageResponse(items, requested, SIZE, total == null ? 0 : total);
    }

    @PreAuthorize(READERS)
    @Transactional(readOnly = true)
    public PaymentSamplePageResponse.Detail executeOne(UUID id) {
        var rows = jdbc.query("""
                select id, reference_code, period_start, period_end
                from app.payment_sample where id = ?
                """, (row, number) -> new Object[] {
                row.getObject(1, UUID.class),
                row.getString(2),
                row.getDate(3).toLocalDate(),
                row.getDate(4).toLocalDate()
        }, id);
        if (rows.isEmpty()) {
            throw new ReportRejected("payment-sample-absent", 404);
        }
        var row = rows.getFirst();
        return new PaymentSamplePageResponse.Detail((UUID) row[0], (String) row[1], (java.time.LocalDate) row[2], (java.time.LocalDate) row[3], lines(id));
    }

    @PreAuthorize(READERS)
    @Transactional(readOnly = true)
    public UUID sampleOf(UUID lineId) {
        var rows = jdbc.query("select sample_id from app.payment_sample_line where id = ?", (row, number) -> row.getObject(1, UUID.class), lineId);
        if (rows.isEmpty()) {
            throw new ReportRejected("payment-sample-absent", 404);
        }
        return rows.getFirst();
    }

    private java.util.List<PaymentSamplePageResponse.Line> lines(UUID id) {
        return jdbc.query("""
                select id, reference_code, operator, amount, result, reason
                from app.payment_sample_line
                where sample_id = ?
                order by reference_code, id
                """, (row, number) -> new PaymentSamplePageResponse.Line(
                row.getObject(1, UUID.class),
                row.getString(2),
                row.getString(3),
                row.getInt(4),
                row.getString(5),
                row.getString(6)), id);
    }
}
