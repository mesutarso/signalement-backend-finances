package cd.gouv.finances.signalements.report.dto;

import cd.gouv.finances.signalements.report.dto.PhotoRequestResponse;
import cd.gouv.finances.signalements.report.dto.PhotoResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        String identifier,
        LocalDateTime reportedAt,
        String title,
        String description,
        String state,
        String priority,
        String assigneeName,
        UUID assigneeId,
        long version,
        @Schema(description = "Present when the caller may see the reporter.")
        String reporterName,
        @Schema(description = "Present when the caller may see the reporter.")
        String reporterEmail,
        @Schema(description = "Present when the caller may see the reporter.")
        String reporterPhone,
        @Schema(description = "Present when the caller may see the reporter.")
        String reporterUserId,
        List<PhotoResponse> photos,
        List<PhotoRequestResponse> photoRequests,
        List<DuplicateAlertResponse> duplicates,
        List<SupervisionReviewResponse> reviews,
        String validationState) {
}
