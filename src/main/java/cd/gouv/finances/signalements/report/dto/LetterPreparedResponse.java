package cd.gouv.finances.signalements.report.dto;

import java.util.UUID;

public record LetterPreparedResponse(UUID id, boolean created) {
}
