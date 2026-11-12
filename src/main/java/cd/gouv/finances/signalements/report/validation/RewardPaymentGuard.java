package cd.gouv.finances.signalements.report.validation;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.prime.BeneficiaryRegistry;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RewardPaymentGuard {
    private final JdbcTemplate jdbc;
    private final BeneficiaryRegistry beneficiaries;

    public void lock() { beneficiaries.lock(); }

    public void requireValidated(UUID reportId) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select validation_state = 'validated' from app.report where id = ?", Boolean.class, reportId))) {
            throw new ReportRejected("reward-validation", 409);
        }
    }

    public void requirePrime(UUID primeId) {
        beneficiaries.lock();
        var invalid = jdbc.queryForObject("""
                select exists(select 1 from app.prime p left join app.report r on r.id = p.report_id
                  where p.id = ? and (p.report_id is null or p.status <> 'approved' or r.validation_state <> 'validated'))
                """, Boolean.class, primeId);
        if (Boolean.TRUE.equals(invalid)) throw new ReportRejected("reward-validation", 409);
    }

    public void requireStatement(UUID statementId) {
        beneficiaries.lock();
        for (var primeId : jdbc.queryForList("select prime_id from app.payment_statement_line where statement_id = ? and not released", UUID.class, statementId)) {
            requirePrime(primeId);
        }
    }
}
