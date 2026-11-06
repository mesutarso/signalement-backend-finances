package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.util.List;

public record ImportPageResponse(
        List<ImportResponse> runs,
        int page,
        int size,
        long total,
        boolean scheduleEnabled,
        boolean active,
        Instant nextRun) {
}
