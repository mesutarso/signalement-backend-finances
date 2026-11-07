package cd.gouv.finances.signalements.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ReexaminationBody(
        @NotBlank @Size(max = 80) String reference,
        @NotBlank @Size(max = 500) String text,
        @NotNull LocalDate receivedOn) {
}
