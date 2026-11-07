package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.util.UUID;

public record PhotoRequestResponse(
        UUID id,
        UUID photoId,
        String referenceCode,
        String reason,
        String basis,
        String status,
        String actorName,
        Instant createdAt,
        Instant resolvedAt,
        String linkedIdentifier,
        String notice) {
}
