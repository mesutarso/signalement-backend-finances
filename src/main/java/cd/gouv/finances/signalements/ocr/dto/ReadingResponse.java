package cd.gouv.finances.signalements.ocr.dto;

import java.util.List;
import java.util.UUID;

public record ReadingResponse(
        UUID id,
        String documentReference,
        String status,
        String quality,
        String schemaVersion,
        String model,
        String errorCode,
        List<FieldResponse> fields) {
}
