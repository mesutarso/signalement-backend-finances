package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AbuseIndexResponse(
        String fingerprint,
        int reportCount,
        List<AbuseReportResponse> reports,
        String explanation,
        String status,
        String reason,
        String actorName,
        Instant recordedAt) {

    public record AbuseReportResponse(UUID id, String identifier, LocalDateTime reportedAt) {
    }
}
