package cd.gouv.finances.signalements.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentModelBody(@NotBlank @Size(max = 16) String kind, @NotBlank @Size(max = 500) String body) {
}
