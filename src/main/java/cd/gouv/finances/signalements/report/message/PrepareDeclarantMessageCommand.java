package cd.gouv.finances.signalements.report.message;

import cd.gouv.finances.signalements.report.ReportReference;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.DeclarantMessage;
import cd.gouv.finances.signalements.report.repository.DeclarantMessageRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PrepareDeclarantMessageCommand {

    private final ReportReference reportReference;
    private final DeclarantMessageRepository declarantMessageRepository;
    private final MessageDrafts messageDrafts;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public UUID execute(String reference, String kind, UUID actorId) {
        var report = reportReference.require(reference);
        var body = messageDrafts.body(kind, report.reference());
        if (declarantMessageRepository.existsByReportIdAndKind(report.id(), kind)) {
            throw new ReportRejected("message-open", 409);
        }
        var message = new DeclarantMessage(UUID.randomUUID(), report.id(), report.reference(), kind, body, actorId, clock.instant());
        declarantMessageRepository.save(message);
        return message.getId();
    }
}
