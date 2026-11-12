package cd.gouv.finances.signalements.report.prime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PrimeContext {

    public static final String READY = "ready";
    public static final String MISSING = "missing";
    public static final String SEVERAL = "several";
    public static final String UNRECORDED = "unrecorded";

    private final JdbcTemplate jdbc;

    public PrimeContext(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Phone(String number, String state) {
    }

    public void lockPaymentIdentity(String declarant) {
        jdbc.queryForObject("select pg_advisory_xact_lock(783962140)", Object.class);
        var pending = jdbc.queryForObject("""
                select exists(select 1 from app.payment_transfer t join app.payment_statement_line l on l.id = t.line_id
                    join app.prime p on p.id = l.prime_id where p.declarant_id = ? and t.environment = 'production'
                    and t.state in ('submitting','pending','unknown'))
                """, Boolean.class, declarant);
        if (Boolean.TRUE.equals(pending)) throw new cd.gouv.finances.signalements.report.ReportRejected("payment-hold", 409);
    }

    public Phone phone(String declarantId, String ruleVersion) {
        var numbers = jdbc.queryForList("""
                select distinct btrim(report.reporter_phone)
                from app.prime prime join app.report report on report.id = prime.report_id
                where prime.declarant_id = ? and prime.report_id is not null and prime.status <> 'withdrawn'
                  and report.reporter_phone is not null and btrim(report.reporter_phone) <> ''
                """, String.class, declarantId);
        if (numbers.isEmpty()) {
            return new Phone(null, MISSING);
        }
        if (numbers.size() > 1) {
            return new Phone(null, SEVERAL);
        }
        return new Phone(numbers.get(0), READY);
    }

}
