package cd.gouv.finances.signalements.report.message;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.sms.SmsResult;
import cd.gouv.finances.signalements.sms.UnikronProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MessageDeliveryStore {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public record Submission(UUID messageId, UUID attemptId, String phone, String body) {
        @Override public String toString() { return "Submission[messageId=" + messageId + ", attemptId=" + attemptId + "]"; }
    }

    @Transactional
    public Submission claim(UUID messageId) { return reserve(messageId, null, null); }

    @Transactional
    public Submission claimPhoto(UUID reportId, UUID requestId) { return reserve(null, reportId, requestId); }

    private Submission reserve(UUID messageId, UUID reportId, UUID requestId) {
        if (requestId != null) {
            // Serialize delivery reservation with replacement/closure of this request.
            var requests = jdbc.query("select id from app.photo_request where id=? and report_id=? and status='open' for update",
                    (r,n)->r.getObject(1,UUID.class),requestId,reportId);
            if (requests.isEmpty()) throw new ReportRejected("photo-request-closed",409);
            var messages = jdbc.query("select id from app.declarant_message where photo_request_id=? and report_id=? and kind='photo'",
                    (r,n)->r.getObject(1,UUID.class),requestId,reportId);
            if (messages.isEmpty()) throw new ReportRejected("sms-absent",404);
            messageId=messages.getFirst();
        }
        final var selectedMessageId = messageId;
        var attemptId = UUID.randomUUID();
        var claimed = jdbc.query("""
                update app.declarant_message m set status = 'sending', send_attempt_id = ?, send_started_at = ?,
                  recipient = (select regexp_replace(coalesce(r.reporter_phone, ''), '[^0-9]', '', 'g') from app.report r where r.id = m.report_id),
                  delivery_code = null, provider_status = null
                where m.id = ? and m.status in ('prepared', 'failed') and (m.kind <> 'photo' or (?::uuid is not null and m.photo_request_id=?))
                returning recipient, body
                """, (row, number) -> new Submission(selectedMessageId, attemptId, UnikronProperties.cleanPhone(row.getString(1)), row.getString(2)),
                attemptId, Timestamp.from(clock.instant()), messageId, requestId, requestId);
        if (claimed.isEmpty()) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from app.declarant_message where id = ? and kind = 'photo')", Boolean.class, messageId))) {
                throw new ReportRejected("photo-request-closed", 409);
            }
            var exists = jdbc.queryForObject("select exists(select 1 from app.declarant_message where id = ?)", Boolean.class, messageId);
            throw new ReportRejected(Boolean.TRUE.equals(exists) ? "sms-state" : "sms-absent", Boolean.TRUE.equals(exists) ? 409 : 404);
        }
        var submission = claimed.getFirst();
        DeclarantMessageText.requireSms(submission.body());
        jdbc.update("update app.declarant_message set recipient = ? where id = ? and send_attempt_id = ?",
                submission.phone(), messageId, attemptId);
        return submission;
    }

    @Transactional
    public void complete(Submission submission, SmsResult result) {
        var changed = jdbc.update("""
                update app.declarant_message set status = ?, sent_at = ?, delivery_code = ?, provider_status = ?
                where id = ? and send_attempt_id = ? and status = 'sending'
                """, result.storedStatus(), "accepted".equals(result.status()) ? Timestamp.from(clock.instant()) : null,
                result.code(), result.httpStatus(), submission.messageId(), submission.attemptId());
        if (changed != 1) throw new ReportRejected("sms-state", 409);
    }
}
