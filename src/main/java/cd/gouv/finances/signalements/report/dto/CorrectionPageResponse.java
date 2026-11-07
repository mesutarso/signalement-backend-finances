package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CorrectionPageResponse(List<Item> items, int page, int size, long total) {

    public record Item(UUID id, String reportReference, String reason, Instant recordedAt) {
    }
}
