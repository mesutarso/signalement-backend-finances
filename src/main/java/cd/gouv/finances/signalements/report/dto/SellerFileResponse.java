package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.util.List;

public record SellerFileResponse(
        String taxId,
        String sellerName,
        boolean namesDiffer,
        Instant latestAt,
        List<SellerFileReportResponse> reports) {
}
