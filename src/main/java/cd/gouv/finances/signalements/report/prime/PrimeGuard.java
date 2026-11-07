package cd.gouv.finances.signalements.report.prime;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PrimeGuard {

    private final JdbcTemplate jdbc;

    public PrimeGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean suspended(String declarantId) {
        if (declarantId == null || declarantId.isBlank()) {
            return false;
        }
        var action = jdbc.query("""
                select action
                from app.prime_suspension
                where declarant_id = ?
                order by recorded_at desc
                limit 1
                """, (row, number) -> row.getString(1), declarantId.trim());
        return !action.isEmpty() && "suspended".equals(action.getFirst());
    }

    public boolean paid(UUID primeId) {
        var paid = jdbc.queryForObject("""
                select exists (
                    select 1
                    from app.payment_statement_line line
                    join app.payment_attempt attempt on attempt.line_id = line.id
                    where line.prime_id = ? and attempt.result = 'paid'
                )
                """, Boolean.class, primeId);
        return Boolean.TRUE.equals(paid);
    }
}
