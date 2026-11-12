package cd.gouv.finances.signalements.report.validation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AssessmentReview(
        @PositiveOrZero long revision,
        UUID photoId,
        @Size(max = 160) String sellerName,
        @Size(max = 40) String sellerTaxId,
        @Size(max = 80) String documentNumber,
        @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}") String documentDate,
        @NotBlank String vatStatus,
        @Size(max = 500) String vatEvidence,
        @NotBlank String normalization,
        @Size(max = 500) String normalizationEvidence,
        @NotBlank String purchaseConformity,
        @Size(max = 500) String purchaseEvidence,
        @NotBlank String vatCharged,
        @Size(max = 500) String vatChargeEvidence,
        @NotBlank String uniqueness,
        @Size(max = 500) String uniquenessEvidence) {
}
