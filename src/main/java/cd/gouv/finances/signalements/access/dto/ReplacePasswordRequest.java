package cd.gouv.finances.signalements.access.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReplacePasswordRequest(
        String currentPassword,
        @NotBlank
        @Size(min = 12, max = 72, message = "Le mot de passe doit contenir de 12 à 72 caractères.")
        @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).*$", message = "Le mot de passe doit contenir une lettre et un chiffre.")
        String password) {
}
