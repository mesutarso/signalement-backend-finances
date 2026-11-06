package cd.gouv.finances.signalements.report.dto;

import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;

public record ImportResponse(
        UUID id,
        String status,
        int createdReports,
        int skippedReports,
        int createdPhotos,
        String errorCode,
        Instant startedAt, Instant finishedAt, LocalDate periodStart, LocalDate periodEnd, String triggerType) {
}
