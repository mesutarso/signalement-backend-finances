package cd.gouv.finances.signalements.report.dto;

import java.util.List;
import java.util.UUID;

public record PaymentLineResponse(UUID id, UUID primeId, int threshold, int amount, String phone, List<PaymentAttemptResponse> attempts) {
}
