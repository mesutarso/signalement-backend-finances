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
        String notice,
        UUID messageId,
        String messageStatus,
        String messageBody) {
    public PhotoRequestResponse(UUID id, UUID photoId, String referenceCode, String reason, String basis,
                                String status, String actorName, Instant createdAt, Instant resolvedAt,
                                String linkedIdentifier, String notice) {
        this(id,photoId,referenceCode,reason,basis,status,actorName,createdAt,resolvedAt,linkedIdentifier,notice,null,null,null);
    }
    public PhotoRequestResponse withMessage(UUID messageId, String messageStatus, String messageBody) {
        return new PhotoRequestResponse(id,photoId,referenceCode,reason,basis,status,actorName,createdAt,resolvedAt,
                linkedIdentifier,notice,messageId,messageStatus,messageBody);
    }
}
