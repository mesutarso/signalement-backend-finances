package cd.gouv.finances.signalements.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeclarantMessageBody(@NotBlank @Size(max = 80) String reference, @NotBlank @Size(max = 16) String kind) {
}
