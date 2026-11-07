package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LetterResponseSummary(
        UUID id,
        LocalDate receivedOn,
        String service,
        String reference,
        String conclusion,
        String contentType,
        Instant recordedAt) {
}
