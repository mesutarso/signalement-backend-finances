package cd.gouv.finances.signalements.report.repository;

import java.util.UUID;

public interface ReportReference {

    UUID getId();

    String getExternalId();
}
