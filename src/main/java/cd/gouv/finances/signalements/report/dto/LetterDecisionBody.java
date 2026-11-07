package cd.gouv.finances.signalements.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LetterDecisionBody(
        @NotBlank(message = "La décision n'est pas acceptée.") @Size(max = 16, message = "La décision n'est pas acceptée.") String result,
        @Size(max = 160, message = "Le destinataire est trop long.") String recipient,
        @Size(max = 500, message = "Le motif est trop long.") String reason) {
}
