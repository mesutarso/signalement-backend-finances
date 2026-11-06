package cd.gouv.finances.signalements.access.dto;

import cd.gouv.finances.signalements.access.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank
        @Size(min = 3, max = 80)
        @Pattern(regexp = "[a-z0-9][a-z0-9._-]*", message = "L'identifiant contient des minuscules, des chiffres, un point, un tiret ou un souligné.")
        String username,
        @NotBlank
        @Size(min = 12, max = 72, message = "Le mot de passe doit contenir de 12 à 72 caractères.")
        @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).*$", message = "Le mot de passe doit contenir une lettre et un chiffre.")
        String password,
        @NotNull Role role) {
}
