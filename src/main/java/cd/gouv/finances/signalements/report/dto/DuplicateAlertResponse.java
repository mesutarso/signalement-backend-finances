package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record DuplicateAlertResponse(
        UUID id,
        UUID otherReportId,
        String otherIdentifier,
        LocalDateTime otherReportedAt,
        String criterion,
        String strength,
        String explanation,
        String status,
        String reason,
        String actorName,
        Instant decidedAt,
        UUID priorityReportId,
        String priorityIdentifier,
        LocalDateTime priorityReportedAt,
        String priorityBasis,
        boolean currentKeepsPriority) {
}
