package cd.gouv.finances.signalements.ocr.command;

import cd.gouv.finances.signalements.ocr.ExtractedArticle;
import cd.gouv.finances.signalements.ocr.ExtractedField;
import cd.gouv.finances.signalements.ocr.ReadingQuality;
import cd.gouv.finances.signalements.ocr.ReadingStatus;
import java.math.BigDecimal;
import java.util.List;

public record ReadingToStore(
        String documentReference,
        String fingerprint,
        ReadingStatus status,
        ReadingQuality quality,
        String model,
        String errorCode,
        BigDecimal confidence,
        List<ExtractedField> fields,
        List<ExtractedArticle> articles) {
}
