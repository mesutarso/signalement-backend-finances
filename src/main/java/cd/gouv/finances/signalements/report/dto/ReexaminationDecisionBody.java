package cd.gouv.finances.signalements.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReexaminationDecisionBody(@NotBlank @Size(max = 16) String result, @Size(max = 500) String comment) {
}
