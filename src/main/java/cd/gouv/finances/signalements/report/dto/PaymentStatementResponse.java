package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PaymentStatementResponse(
        UUID id,
        String reference,
        String status,
        int total,
        Instant preparedAt,
        Instant controlledAt,
        Instant visaedAt,
        Instant authorizedAt,
        LocalDate dueOn,
        String reason,
        List<PaymentLineResponse> lines,
        List<PaymentExclusionResponse> exclusions) {
}
