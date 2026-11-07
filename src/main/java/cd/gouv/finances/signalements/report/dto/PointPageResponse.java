package cd.gouv.finances.signalements.report.dto;

import java.util.List;

public record PointPageResponse(List<PointMovementResponse> items, int page, int size, long total, int net) {
}
