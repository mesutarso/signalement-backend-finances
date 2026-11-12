package cd.gouv.finances.signalements.report.dto;

import java.util.List;

public record RewardPageResponse(List<RewardResponse> items, int page, int size, long total) {
}
