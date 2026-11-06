package cd.gouv.finances.signalements.access.dto;

import cd.gouv.finances.signalements.access.Role;

public record UpdateAccountRequest(Role role, Boolean enabled) {
}
