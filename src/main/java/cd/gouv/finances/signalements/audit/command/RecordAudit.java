package cd.gouv.finances.signalements.audit.command;

import cd.gouv.finances.signalements.audit.entity.AuditEntry;
import cd.gouv.finances.signalements.audit.repository.AuditEntryRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RecordAudit {

    private final AuditEntryRepository auditEntryRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void allowed(UUID actorId, String action, String objectType, String objectId) {
        write(actorId, action, objectType, objectId, "allowed");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refused(UUID actorId, String action, String objectType, String objectId) {
        write(actorId, action, objectType, objectId, "refused");
    }

    private void write(UUID actorId, String action, String objectType, String objectId, String result) {
        auditEntryRepository.save(new AuditEntry(
                UUID.randomUUID(),
                actorId,
                action,
                objectType,
                objectId,
                result,
                clock.instant()));
    }
}
