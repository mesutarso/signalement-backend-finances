package cd.gouv.finances.signalements.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AbuseNoteBody(
        @NotBlank(message = "La décision n'est pas acceptée.")
        @Size(max = 16, message = "La décision n'est pas acceptée.")
        String decision,
        @NotBlank(message = "Indiquez le motif de l'examen.")
        @Size(max = 500, message = "Le motif est trop long.")
        String reason) {
}
