package cd.gouv.finances.signalements.ocr.mapper;

import cd.gouv.finances.signalements.ocr.DocumentField;
import cd.gouv.finances.signalements.ocr.dto.ArticleResponse;
import cd.gouv.finances.signalements.ocr.dto.ReadingResponse;
import cd.gouv.finances.signalements.ocr.entity.DocumentReading;
import cd.gouv.finances.signalements.ocr.entity.ReadingArticle;
import cd.gouv.finances.signalements.ocr.entity.StoredField;
import java.util.Comparator;

public final class ReadingMapper {

    private ReadingMapper() {
    }

    public static ReadingResponse toResponse(DocumentReading reading) {
        return new ReadingResponse(
                reading.getId(),
                reading.getDocumentReference(),
                reading.getStatus().name(),
                reading.getQuality() == null ? null : reading.getQuality().name(),
                reading.getConfidence(),
                value(reading, DocumentField.SELLER_NAME),
                value(reading, DocumentField.SELLER_TAX_ID),
                value(reading, DocumentField.DOCUMENT_DATE),
                value(reading, DocumentField.TOTAL_AMOUNT),
                value(reading, DocumentField.DOCUMENT_NUMBER),
                value(reading, DocumentField.READ_CODE),
                value(reading, DocumentField.CURRENCY),
                value(reading, DocumentField.NET_AMOUNT),
                value(reading, DocumentField.VAT_AMOUNT),
                value(reading, DocumentField.VAT_RATE),
                value(reading, DocumentField.PAID_AMOUNT),
                value(reading, DocumentField.DEF_CODE),
                value(reading, DocumentField.DEF_NUMBER),
                reading.getModel(),
                reading.getSchemaVersion(),
                reading.getArticles().stream()
                        .sorted(Comparator.comparingInt(ReadingArticle::getPosition))
                        .map(article -> new ArticleResponse(article.getDesignation(), article.getAmount()))
                        .toList(),
                reading.getErrorCode());
    }

    private static String value(DocumentReading reading, DocumentField field) {
        return reading.getFields().stream()
                .filter(stored -> field.nameKey().equals(stored.getName()) && stored.isPresent())
                .map(StoredField::getValue)
                .findFirst()
                .orElse(null);
    }
}
