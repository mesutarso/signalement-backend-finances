package cd.gouv.finances.signalements.report.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SeriesIndexResponse(LocalDate reportedOn, int reportCount, List<IndexedReport> reports, String explanation) {

    public record IndexedReport(UUID id, String reference) {
    }
}
