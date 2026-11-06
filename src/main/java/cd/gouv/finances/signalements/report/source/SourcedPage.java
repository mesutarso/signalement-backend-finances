package cd.gouv.finances.signalements.report.source;

import java.util.List;

public record SourcedPage(List<SourcedReport> reports, boolean last) {
}
