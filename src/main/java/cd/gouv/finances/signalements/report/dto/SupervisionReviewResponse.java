package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.util.UUID;

public record SupervisionReviewResponse(
        UUID id,
        UUID qualificationId,
        String result,
        String reason,
        String actorName,
        Instant recordedAt) {
}
