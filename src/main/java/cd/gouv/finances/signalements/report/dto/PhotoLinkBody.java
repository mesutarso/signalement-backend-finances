package cd.gouv.finances.signalements.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PhotoLinkBody(
        @NotBlank(message = "Sélectionnez le signalement reçu.")
        @Size(max = 80, message = "Le signalement sélectionné est invalide.")
        String referenceCode) {
}
