package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LetterSummaryResponse(
        UUID id,
        String reference,
        LocalDate periodStart,
        LocalDate periodEnd,
        String status,
        String recipient,
        String reason,
        Instant preparedAt,
        Instant decidedAt) {
}
