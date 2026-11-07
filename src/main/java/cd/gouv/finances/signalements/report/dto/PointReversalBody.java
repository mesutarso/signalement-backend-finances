package cd.gouv.finances.signalements.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PointReversalBody(
        @NotBlank(message = "Indiquez le motif de l'annulation.") @Size(max = 500, message = "Le motif est trop long.") String reason) {
}
