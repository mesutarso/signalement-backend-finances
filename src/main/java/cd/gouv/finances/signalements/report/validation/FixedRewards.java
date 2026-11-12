package cd.gouv.finances.signalements.report.validation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Invoked inside the assessment transaction, after acquiring the beneficiary lock. */
@Component
@RequiredArgsConstructor
public class FixedRewards {
    private final JdbcTemplate jdbc;
    private final RewardSettings settings;
    private final Clock clock;

    public void reconcile(UUID reportId, String decision, UUID actorId) {
        var now = Timestamp.from(clock.instant());
        if (!"validated".equals(decision)) {
            jdbc.update("""
                    update app.prime p set status = 'withdrawn', actor_id = ?, updated_at = ?
                    where report_id = ? and status <> 'withdrawn' and not exists (
                      select 1 from app.payment_statement_line l join app.payment_attempt a on a.line_id = l.id
                      where l.prime_id = p.id and a.result = 'paid')
                    """, actorId, now, reportId);
            return;
        }
        var declarant = jdbc.queryForObject("select nullif(btrim(reporter_user_id), '') from app.report where id = ?", String.class, reportId);
        if (declarant == null) declarant = "report:" + reportId;
        var phone = jdbc.queryForObject("select nullif(btrim(reporter_phone), '') from app.report where id = ?", String.class, reportId);
        var current = settings.active();
        jdbc.update("""
                insert into app.prime (id, declarant_id, phone, rule_version, threshold, amount, status, actor_id,
                    recorded_at, updated_at, report_id, reward_setting_id, invoice_key)
                values (?, ?, ?, 'fixed-reward', 0, ?, 'proposed', ?, ?, ?, ?, ?, ?)
                on conflict (report_id) where report_id is not null do nothing
                """, UUID.randomUUID(), declarant, phone, current.amount(), actorId, now, now, reportId, current.id(), invoiceKey(reportId));
        // Keep the original amount and setting when the same report is validated again.
        jdbc.update("""
                update app.prime p set status = 'proposed', phone = ?, actor_id = ?, updated_at = ?
                where report_id = ? and status = 'withdrawn' and not exists (
                  select 1 from app.payment_statement_line l join app.payment_attempt a on a.line_id = l.id
                  where l.prime_id = p.id and a.result = 'paid')
                """, phone, actorId, now, reportId);
    }

    public boolean alreadyRewarded(UUID reportId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from app.prime where invoice_key = ? and report_id <> ?)",
                Boolean.class, invoiceKey(reportId), reportId));
    }

    private String invoiceKey(UUID reportId) {
        var material = jdbc.queryForObject("""
                select case when nullif(btrim(a.document_number), '') is not null then
                  lower(coalesce(nullif(btrim(a.seller_tax_id), ''), btrim(a.seller_name))) || chr(31) || lower(btrim(a.document_number))
                    || chr(31) || coalesce(a.document_date::text, '')
                  else (select fingerprint from app.report_photo where id = a.photo_id) end
                from app.report_assessment a where report_id = ?
                """, String.class, reportId);
        if (material == null) material = reportId.toString();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
