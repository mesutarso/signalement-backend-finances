package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PaymentSamplePageResponse(List<Item> items, int page, int size, long total) {

    public record Item(UUID id, String reference, LocalDate periodStart, LocalDate periodEnd, int lines, Instant preparedAt) {
    }

    public record Line(UUID id, String reference, String operator, int amount, String result, String reason) {
    }

    public record Detail(UUID id, String reference, LocalDate periodStart, LocalDate periodEnd, List<Line> lines) {
    }
}
