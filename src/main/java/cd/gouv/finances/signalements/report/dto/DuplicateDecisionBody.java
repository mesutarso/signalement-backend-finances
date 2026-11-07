package cd.gouv.finances.signalements.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DuplicateDecisionBody(
        @NotBlank(message = "La décision n'est pas acceptée.")
        @Pattern(regexp = "confirm|dismiss", message = "La décision n'est pas acceptée.")
        String decision,
        @NotBlank(message = "Indiquez le motif de la décision.")
        @Size(max = 500, message = "Le motif est trop long.")
        String reason) {
}
