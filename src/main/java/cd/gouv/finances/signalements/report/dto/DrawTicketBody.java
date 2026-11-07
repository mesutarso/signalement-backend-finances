package cd.gouv.finances.signalements.report.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record DrawTicketBody(@NotNull LocalDate month) {
}
