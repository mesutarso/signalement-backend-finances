package cd.gouv.finances.signalements.report.payment;

import cd.gouv.finances.signalements.report.prime.PrimeContext;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentSourceLoader {

    private final JdbcTemplate jdbc;

    public PaymentSourceLoader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Candidate(UUID primeId, int threshold, int amount, String primePhone, String payoutState, String payoutPhone, boolean active) {
    }

    public List<Candidate> candidates() {
        return jdbc.query("""
                select prime.id, prime.threshold, prime.amount, prime.phone, payout.state, payout.phone,
                       exists (
                           select 1
                           from app.payment_statement_line line
                           join app.payment_statement statement on statement.id = line.statement_id
                           where line.prime_id = prime.id
                             and line.released = false
                             and statement.status in ('draft', 'controlled', 'visaed', 'authorized')
                       )
                from app.prime prime
                left join app.payout_number payout on payout.declarant_id = prime.declarant_id
                where prime.status = 'approved'
                  and prime.declarant_id not in (
                      select declarant_id from (
                          select distinct on (declarant_id) declarant_id, action
                          from app.prime_suspension
                          order by declarant_id, recorded_at desc
                      ) latest
                      where action = 'suspended'
                  )
                  and (payout.lost_at is null)
                order by prime.threshold, prime.id
                """, (row, number) -> new Candidate(
                row.getObject(1, UUID.class),
                row.getInt(2),
                row.getInt(3),
                row.getString(4),
                row.getString(5),
                row.getString(6),
                row.getBoolean(7)));
    }

    public static boolean eligible(Candidate candidate) {
        return !candidate.active()
                && PrimeContext.READY.equals(candidate.payoutState())
                && candidate.payoutPhone() != null
                && candidate.payoutPhone().equals(candidate.primePhone());
    }

    public static String exclusionReason(Candidate candidate) {
        if (candidate.active()) {
            return "Cette prime est déjà dans un état.";
        }
        if (PrimeContext.SEVERAL.equals(candidate.payoutState())) {
            return "Le déclarant a plusieurs numéros.";
        }
        if (!PrimeContext.READY.equals(candidate.payoutState()) || candidate.payoutPhone() == null) {
            return "Le numéro de paiement manque.";
        }
        return "Le numéro de paiement a changé.";
    }
}
