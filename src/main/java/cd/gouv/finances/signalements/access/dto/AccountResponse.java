package cd.gouv.finances.signalements.access.dto;

import java.util.UUID;
import java.util.List;
import java.time.Instant;
import cd.gouv.finances.signalements.access.Permission;
import cd.gouv.finances.signalements.access.Role;

public record AccountResponse(UUID id, String username, String role, boolean enabled,
                              List<String> permissions, Instant lockedUntil, Instant createdAt) {
    public AccountResponse(UUID id, String username, String role, boolean enabled) {
        this(id, username, role, enabled, Permission.forRole(Role.valueOf(role)), null, null);
    }
}
