package cd.gouv.finances.signalements.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record PhotoReadingResponse(
        String status,
        String quality,
        BigDecimal confidence,
        String sellerName,
        String sellerTaxId,
        String documentDate,
        String amount,
        String documentNumber,
        String code,
        String currency,
        String netAmount,
        String vatAmount,
        String vatRate,
        String paidAmount,
        String defCode,
        String defNumber,
        String model,
        String schemaVersion,
        List<PhotoArticleResponse> articles,
        String errorCode) {
}
