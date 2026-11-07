package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record SellerFileReportResponse(
        UUID reportId,
        String identifier,
        LocalDateTime reportedAt,
        String title,
        String decision,
        String finding,
        String proof,
        String sellerName,
        String justification,
        Instant qualifiedAt) {
}
