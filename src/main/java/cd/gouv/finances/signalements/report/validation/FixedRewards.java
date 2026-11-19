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
        cd.gouv.finances.signalements.report.payment.PaymentHold.requireNoPending(jdbc, reportId);
        var now = Timestamp.from(clock.instant());
        if (!"validated".equals(decision)) {
            jdbc.update("""
                    update app.prime p set status = 'withdrawn', actor_id = ?, updated_at = ?
                    where report_id = ? and status <> 'withdrawn' and not exists (
                      select 1 from app.payment_statement_line l join app.payment_attempt a on a.line_id = l.id
                      where l.prime_id = p.id and a.result = 'paid' and a.reference_code not like 'SIM-%')
                    """, actorId, now, reportId);
            releaseDuplicateReservations(java.util.List.of(reportId));
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
                update app.prime p set status = 'proposed', phone = ?, actor_id = ?, updated_at = ?, invoice_key = ?
                where report_id = ? and status = 'withdrawn' and not exists (
                  select 1 from app.payment_statement_line l join app.payment_attempt a on a.line_id = l.id
                  where l.prime_id = p.id and a.result = 'paid' and a.reference_code not like 'SIM-%')
                """, phone, actorId, now, invoiceKey(reportId), reportId);
    }

    /** Release only unpaid claims that are now confirmed losing duplicates; retain every reward row. */
    public void releaseDuplicateReservations(java.util.List<UUID> reportIds) {
        if (reportIds.isEmpty()) return;
        var marks=String.join(",",reportIds.stream().map(id->"?").toList());
        var arguments=new java.util.ArrayList<Object>();
        arguments.add(ValidationRules.AUTOMATION); arguments.addAll(reportIds);
        jdbc.update("""
                update app.prime p set status='withdrawn',invoice_key=null,actor_id=?,updated_at=current_timestamp
                where p.report_id in (%s) and p.invoice_key is not null
                  and exists(select 1 from app.duplicate_alert d where d.status='confirmed'
                    and (d.report_id=p.report_id or d.other_report_id=p.report_id) and d.priority_report_id<>p.report_id)
                  and not exists(select 1 from app.payment_statement_line l join app.payment_attempt a on a.line_id=l.id
                    where l.prime_id=p.id and a.result='paid' and a.reference_code not like 'SIM-%%')
                  and not exists(select 1 from app.payment_statement_line l join app.payment_transfer t on t.line_id=l.id
                    where l.prime_id=p.id and t.environment='production' and t.state in ('submitting','pending','unknown'))
                """.formatted(marks),arguments.toArray());
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
