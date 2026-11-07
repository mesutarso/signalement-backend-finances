package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PaymentPageResponse(List<Item> items, int page, int size, long total) {

    public record Item(UUID id, String reference, String status, int total, Instant preparedAt, LocalDate dueOn) {
    }
}
