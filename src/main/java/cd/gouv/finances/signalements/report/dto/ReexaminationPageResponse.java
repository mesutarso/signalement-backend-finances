package cd.gouv.finances.signalements.report.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReexaminationPageResponse(List<Item> items, int page, int size, long total) {

    public record Item(UUID id, String reference, String reportReference, String requestText, LocalDate receivedOn, boolean onTime, String status, String comment) {
    }
}
