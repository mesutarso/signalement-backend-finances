package cd.gouv.finances.signalements.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record PaymentAttemptBody(
        @NotNull(message = "Cette prime n'est pas dans cet état.") UUID lineId,
        @NotBlank(message = "Choisissez un moyen de paiement.") @Size(max = 16, message = "Choisissez un moyen de paiement.") String operator,
        @NotBlank(message = "Le résultat n'est pas accepté.") @Size(max = 16, message = "Le résultat n'est pas accepté.") String result,
        @Size(max = 500, message = "Indiquez le motif.") String reason) {
}
