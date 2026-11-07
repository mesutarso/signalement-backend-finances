package cd.gouv.finances.signalements.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PhotoLinkBody(
        @NotBlank(message = "Indiquez la référence du signalement reçu.")
        @Size(max = 80, message = "La référence est trop longue.")
        String referenceCode) {
}
