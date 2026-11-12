package cd.gouv.finances.signalements.report.prime;

import cd.gouv.finances.signalements.report.ReportReference;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.Prime;
import cd.gouv.finances.signalements.report.rule.ActiveRule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BeneficiaryRegistry {
    private final JdbcTemplate jdbc;
    private final ReportReference reportReference;
    private final ActiveRule activeRule;
    private final Clock clock;

    public record Situation(boolean verified, int accounts, long reservedAmount, int ceiling, long remainder) { }

    // One lock order for identity changes and approvals also prevents concurrent cap overruns.
    public void lock() {
        jdbc.queryForObject("select pg_advisory_xact_lock(783962140)", Object.class);
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public Situation read(String reference) {
        var declarant = declarant(reference);
        var key = key(declarant);
        var ceiling = activeRule.requireActive().ceiling();
        if (key == null) return new Situation(false, 0, 0, ceiling, ceiling);
        var count = jdbc.queryForObject("select count(*) from app.beneficiary_account where beneficiary_key = ?", Integer.class, key);
        var reserved = reserved(key, null);
        return new Situation(true, count == null ? 0 : count, reserved, ceiling, Math.max(0L, ceiling - reserved));
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public void verify(String reference, String beneficiaryReference, String evidence, UUID actorId) {
        var kept = beneficiaryReference == null ? "" : beneficiaryReference.trim().toUpperCase(Locale.ROOT);
        var proof = evidence == null ? "" : evidence.trim();
        if (kept.length() < 3 || kept.length() > 80 || proof.isEmpty() || proof.length() > 160) {
            throw new ReportRejected("beneficiary-evidence", 400);
        }
        lock();
        var declarant = declarant(reference);
        var identity = fingerprint(kept);
        var existing = key(declarant);
        if (existing != null) {
            if (!existing.equals(identity)) throw new ReportRejected("beneficiary-locked", 409);
            return;
        }
        jdbc.update("""
                insert into app.beneficiary_account (declarant_id, beneficiary_key, evidence_reference, verified_by, verified_at)
                values (?, ?, ?, ?, ?)
                """, declarant, identity, proof, actorId, java.sql.Timestamp.from(clock.instant()));
    }

    public void requireCapacity(Prime prime) {
        var identity = key(prime.getDeclarantId());
        if (identity == null) throw new ReportRejected("beneficiary-unverified", 409);
        var ceiling = activeRule.requireActive().ceiling();
        if (reserved(identity, prime.getId()) + prime.getAmount() > ceiling) {
            throw new ReportRejected("beneficiary-ceiling", 409);
        }
    }

    private long reserved(String key, UUID excludedPrime) {
        var amount = jdbc.queryForObject("""
                select coalesce(sum(prime.amount), 0)
                from app.prime prime
                join app.beneficiary_account account on account.declarant_id = prime.declarant_id
                where account.beneficiary_key = ? and (?::uuid is null or prime.id <> ?::uuid)
                  and (prime.status = 'approved' or exists (
                    select 1 from app.payment_statement_line line
                    join app.payment_attempt attempt on attempt.line_id = line.id
                    where line.prime_id = prime.id and attempt.result = 'paid'))
                """, Long.class, key, excludedPrime, excludedPrime);
        return amount == null ? 0 : amount;
    }

    private String declarant(String reference) {
        var match = reportReference.require(reference);
        var id = jdbc.queryForObject("select nullif(btrim(reporter_user_id), '') from app.report where id = ?", String.class, match.id());
        if (id == null) throw new ReportRejected("points-declarant", 409);
        return id;
    }

    private String key(String declarant) {
        var rows = jdbc.queryForList("select beneficiary_key from app.beneficiary_account where declarant_id = ?", String.class, declarant);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
