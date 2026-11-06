package cd.gouv.finances.signalements.report.dto;

import java.util.List;

public record ReportPageResponse(List<ReportSummaryResponse> reports, int page, int size, long total) {
}
