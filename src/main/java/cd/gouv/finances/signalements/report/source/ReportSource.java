package cd.gouv.finances.signalements.report.source;

import java.time.LocalDate;

public interface ReportSource {

    String NAME = "facno";

    SourcedPage read(LocalDate start, LocalDate end, int pageNumber, int pageSize);
}
