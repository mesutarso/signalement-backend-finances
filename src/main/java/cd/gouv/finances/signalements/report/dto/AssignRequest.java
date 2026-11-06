package cd.gouv.finances.signalements.report.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignRequest(@NotNull Long version, UUID assigneeId, String priority) {
}
