package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.util.List;

public record PrimeSuspensionResponse(List<Item> items) {

    public record Item(String reference, String action, String reason, Instant recordedAt) {
    }
}
