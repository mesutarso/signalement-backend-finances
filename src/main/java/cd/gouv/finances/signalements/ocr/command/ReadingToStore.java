package cd.gouv.finances.signalements.ocr.command;

import cd.gouv.finances.signalements.ocr.ExtractedField;
import cd.gouv.finances.signalements.ocr.ReadingQuality;
import cd.gouv.finances.signalements.ocr.ReadingStatus;
import java.util.List;

public record ReadingToStore(
        String documentReference,
        String fingerprint,
        ReadingStatus status,
        ReadingQuality quality,
        String model,
        String errorCode,
        List<ExtractedField> fields) {
}
