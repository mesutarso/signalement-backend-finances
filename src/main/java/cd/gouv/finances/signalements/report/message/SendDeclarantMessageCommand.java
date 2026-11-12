package cd.gouv.finances.signalements.report.message;

import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.sms.SmsResult;
import cd.gouv.finances.signalements.sms.UnikronSmsGateway;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SendDeclarantMessageCommand {
    private final MessageDeliveryStore store;
    private final UnikronSmsGateway gateway;
    private final RecordAudit audit;

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    public SmsResult execute(UUID messageId, UUID actorId) {
        var submission = store.claim(messageId);
        // No database transaction or pooled connection is held during the network call.
        var result = gateway.send(submission.phone(), submission.body());
        store.complete(submission, result);
        audit.allowed(actorId, "sms-" + result.status(), "message", messageId.toString());
        return result;
    }
}
