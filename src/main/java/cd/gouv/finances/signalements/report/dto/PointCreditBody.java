package cd.gouv.finances.signalements.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PointCreditBody(
        @NotBlank(message = "Indiquez la référence de la confirmation.") @Size(max = 80, message = "La référence est trop longue.") String reference) {
}
