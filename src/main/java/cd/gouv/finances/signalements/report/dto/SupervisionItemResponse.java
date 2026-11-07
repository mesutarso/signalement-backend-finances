package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record SupervisionItemResponse(
        UUID reportId,
        String identifier,
        LocalDateTime reportedAt,
        String title,
        UUID qualificationId,
        String decision,
        String finding,
        String proof,
        String justification,
        String qualifierName,
        Instant qualifiedAt,
        String reviewResult,
        String reviewReason,
        String reviewerName,
        Instant reviewedAt) {
}
