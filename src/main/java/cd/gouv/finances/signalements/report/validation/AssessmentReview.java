package cd.gouv.finances.signalements.report.validation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AssessmentReview(
        @PositiveOrZero long revision,
        @Size(max = 160) String sellerName,
        @Size(max = 40) String sellerTaxId,
        @Size(max = 80) String documentNumber,
        @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}") String documentDate,
        @NotBlank String normalization) {
}
