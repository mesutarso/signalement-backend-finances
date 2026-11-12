package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RewardResponse(
        UUID id,
        int amount,
        String status,
        String phone,
        Instant recordedAt,
        List<PrimeReviewResponse> reviews,
        UUID reportId,
        String reportReference) {
}
