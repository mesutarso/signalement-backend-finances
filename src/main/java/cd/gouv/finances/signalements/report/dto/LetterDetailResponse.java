package cd.gouv.finances.signalements.report.dto;

import java.util.List;

public record LetterDetailResponse(LetterSummaryResponse letter, List<LetterLineResponse> lines, List<LetterResponseSummary> responses) {
}
