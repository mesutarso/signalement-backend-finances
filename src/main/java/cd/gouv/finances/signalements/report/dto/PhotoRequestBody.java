package cd.gouv.finances.signalements.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PhotoRequestBody(
        @NotBlank(message = "Indiquez pourquoi la pièce est illisible.")
        @Size(max = 500, message = "Le motif est trop long.")
        String reason) {
}
