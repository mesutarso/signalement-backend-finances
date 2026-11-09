package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.util.List;

public record DocumentModelResponse(List<Item> models) {

    public record Item(String kind, String body, Instant recordedAt) {
    }
}
