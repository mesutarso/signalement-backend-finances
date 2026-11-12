package cd.gouv.finances.signalements.report.payment;

import cd.gouv.finances.signalements.report.ReportRejected;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PaymentHold {
    private PaymentHold() { }

    /** Called under the shared beneficiary transaction lock, before changes can commit. */
    public static void requireNoPending(JdbcTemplate jdbc, UUID reportId) {
        var pending = jdbc.queryForObject("""
                select exists(select 1 from app.payment_transfer t
                    join app.payment_statement_line l on l.id = t.line_id join app.prime p on p.id = l.prime_id
                    where p.report_id = ? and t.environment = 'production' and t.state in ('submitting','pending','unknown'))
                """, Boolean.class, reportId);
        if (Boolean.TRUE.equals(pending)) throw new ReportRejected("payment-hold", 409);
    }
}
