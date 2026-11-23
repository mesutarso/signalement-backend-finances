package cd.gouv.finances.signalements.report.validation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AssessmentResolution(
        @PositiveOrZero long revision,
        @NotBlank @Size(max = 16) String decision,
        @NotBlank @Size(max = 32) String code) { }
