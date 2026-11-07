package cd.gouv.finances.signalements.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrimeReviewBody(
        @NotBlank(message = "La décision n'est pas acceptée.") @Size(max = 16, message = "La décision n'est pas acceptée.") String result,
        @Size(max = 500, message = "Le motif est trop long.") String reason) {
}
