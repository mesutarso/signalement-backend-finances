package cd.gouv.finances.signalements.report.command;

import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.message.MessageDeliveryStore;
import cd.gouv.finances.signalements.sms.SmsResult;
import cd.gouv.finances.signalements.sms.UnikronSmsGateway;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SendPhotoRequestCommand {
    private final MessageDeliveryStore store;
    private final UnikronSmsGateway gateway;
    private final RecordAudit audit;

    @PreAuthorize("hasAuthority('report.review')")
    public SmsResult execute(UUID reportId, UUID requestId, UUID actorId) {
        var submission=store.claimPhoto(reportId,requestId);
        var result=gateway.send(submission.phone(),submission.body());
        store.complete(submission,result);
        audit.allowed(actorId,"photo-sms-"+result.status(),"report",reportId.toString());
        return result;
    }
}
