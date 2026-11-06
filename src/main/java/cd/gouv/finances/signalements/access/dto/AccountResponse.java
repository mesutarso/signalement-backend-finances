package cd.gouv.finances.signalements.access.dto;

import java.util.UUID;

public record AccountResponse(UUID id, String username, String role, boolean enabled) {
}
