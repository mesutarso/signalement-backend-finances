package cd.gouv.finances.signalements.report.dto;

import cd.gouv.finances.signalements.report.dto.SeriesIndexResponse.IndexedReport;
import java.util.List;

public record AccountIndexResponse(int accountCount, int reportCount, List<IndexedReport> reports, String explanation) {
}
