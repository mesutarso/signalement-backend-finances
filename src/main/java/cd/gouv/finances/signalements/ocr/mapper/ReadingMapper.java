package cd.gouv.finances.signalements.ocr.mapper;

import cd.gouv.finances.signalements.ocr.dto.FieldResponse;
import cd.gouv.finances.signalements.ocr.dto.ReadingResponse;
import cd.gouv.finances.signalements.ocr.entity.DocumentReading;
import java.util.Comparator;

public final class ReadingMapper {

    private ReadingMapper() {
    }

    public static ReadingResponse toResponse(DocumentReading reading) {
        var fields = reading.getFields().stream()
                .sorted(Comparator.comparing(stored -> stored.getName()))
                .map(field -> new FieldResponse(field.getName(), field.getValue(), field.isPresent()))
                .toList();
        return new ReadingResponse(
                reading.getId(),
                reading.getDocumentReference(),
                reading.getStatus().name(),
                reading.getQuality() == null ? null : reading.getQuality().name(),
                reading.getSchemaVersion(),
                reading.getModel(),
                reading.getErrorCode(),
                fields);
    }
}
