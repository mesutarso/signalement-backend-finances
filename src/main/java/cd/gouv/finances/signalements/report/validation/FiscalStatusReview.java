package cd.gouv.finances.signalements.report.validation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record FiscalStatusReview(
        @PositiveOrZero long revision,
        @NotBlank @Pattern(regexp = "registered|unregistered") String vatStatus,
        @NotBlank @Size(max = 500) String vatEvidence) {
}
