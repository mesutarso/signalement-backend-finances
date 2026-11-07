package cd.gouv.finances.signalements.ocr.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ReadingResponse(
        UUID id,
        String documentReference,
        String status,
        String quality,
        BigDecimal confidence,
        String sellerName,
        String sellerTaxId,
        String documentDate,
        String amount,
        String documentNumber,
        String code,
        List<ArticleResponse> articles,
        String errorCode) {
}
