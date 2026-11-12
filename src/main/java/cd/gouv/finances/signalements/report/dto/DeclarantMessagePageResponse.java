package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DeclarantMessagePageResponse(List<Item> items, int page, int size, long total) {

    public record Item(UUID id, String reportReference, String kind, String body, Instant preparedAt,
                       String status, Instant sentAt, String deliveryCode) {
    }
}
