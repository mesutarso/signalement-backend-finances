package cd.gouv.finances.signalements.report.dto;

import java.util.List;

public record SupervisionPageResponse(List<SupervisionItemResponse> items, int page, int size, long total) {
}
