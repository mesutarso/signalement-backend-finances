package cd.gouv.finances.signalements.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record DrawBody(
        @NotBlank @Size(max = 16) String kind,
        LocalDate month,
        @NotBlank @Size(max = 160) String witness,
        int campaign,
        int intermediate,
        int motorcycles,
        int cars) {
}
