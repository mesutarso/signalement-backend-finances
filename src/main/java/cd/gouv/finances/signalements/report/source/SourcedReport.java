package cd.gouv.finances.signalements.report.source;

import java.time.LocalDateTime;
import java.util.List;

public record SourcedReport(
        String externalId,
        LocalDateTime reportedAt,
        String title,
        String description,
        String reporterName,
        String reporterEmail,
        String reporterPhone,
        String reporterUserId,
        List<SourcedPhoto> photos) {
}
