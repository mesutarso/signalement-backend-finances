package cd.gouv.finances.signalements.report.dto;

import java.util.List;

public record LetterPageResponse(List<LetterSummaryResponse> items, int page, int size, long total) {
}
