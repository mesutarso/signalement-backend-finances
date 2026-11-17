package cd.gouv.finances.signalements.access.dto;

import cd.gouv.finances.signalements.access.Role;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAccountRequest(Role role, Boolean enabled,
        @Size(min = 3, max = 80) @Pattern(regexp = "[a-z0-9][a-z0-9._-]*") String username) {
    public UpdateAccountRequest(Role role, Boolean enabled) { this(role, enabled, null); }
}
