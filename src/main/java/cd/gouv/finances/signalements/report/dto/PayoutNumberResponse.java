package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.util.UUID;

public record PayoutNumberResponse(UUID id, String phone, String state, Instant updatedAt) {
}
