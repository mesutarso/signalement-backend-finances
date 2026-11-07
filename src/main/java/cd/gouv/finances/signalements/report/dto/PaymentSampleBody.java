package cd.gouv.finances.signalements.report.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PaymentSampleBody(@NotNull LocalDate periodStart, @NotNull LocalDate periodEnd) {
}
