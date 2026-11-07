package cd.gouv.finances.signalements.report.mapper;

import cd.gouv.finances.signalements.ocr.dto.ReadingResponse;
import cd.gouv.finances.signalements.report.dto.PhotoArticleResponse;
import cd.gouv.finances.signalements.report.dto.PhotoReadingResponse;

public final class PhotoReadingMapper {

    private PhotoReadingMapper() {
    }

    public static PhotoReadingResponse from(ReadingResponse reading) {
        if (reading == null) {
            return null;
        }
        return new PhotoReadingResponse(
                reading.status(),
                reading.quality(),
                reading.confidence(),
                reading.sellerName(),
                reading.sellerTaxId(),
                reading.documentDate(),
                reading.amount(),
                reading.documentNumber(),
                reading.code(),
                reading.articles().stream()
                        .map(article -> new PhotoArticleResponse(article.designation(), article.amount()))
                        .toList(),
                reading.errorCode());
    }
}
