package cd.gouv.finances.signalements.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditEntryResponse(
        UUID id,
        String actorName,
        String action,
        String objectType,
        String objectId,
        String result,
        Instant recordedAt) {
}
