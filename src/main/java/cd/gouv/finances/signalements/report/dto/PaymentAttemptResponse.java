package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentAttemptResponse(UUID id, String result, String reference, String reason, Instant recordedAt, String operator) {
}
