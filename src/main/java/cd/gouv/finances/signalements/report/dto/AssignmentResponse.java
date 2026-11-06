package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.util.UUID;

public record AssignmentResponse(
        Instant recordedAt,
        String actorName,
        String assigneeName,
        String previousAssigneeName,
        String priority,
        String state) {
}
