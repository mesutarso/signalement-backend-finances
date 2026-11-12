package cd.gouv.finances.signalements.report.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReportSummaryResponse(
        UUID id,
        String identifier,
        LocalDateTime reportedAt,
        String title,
        String state,
        String priority,
        String assigneeName,
        long version,
        String reporterName,
        String reporterEmail,
        String reporterPhone,
        String validationState) {
}
