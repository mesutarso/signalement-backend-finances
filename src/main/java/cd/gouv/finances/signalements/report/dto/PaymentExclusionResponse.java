package cd.gouv.finances.signalements.report.dto;

import java.util.UUID;

public record PaymentExclusionResponse(UUID primeId, String reason) {
}
