package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.source.ReportSource;
import cd.gouv.finances.signalements.report.source.StoredPhotoIndex;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaStoredPhotoIndex implements StoredPhotoIndex {

    private final ReportPhotoRepository reportPhotoRepository;

    @Override
    public boolean contains(String reportExternalId, String photoExternalId) {
        return reportPhotoRepository.existsByReport_SourceAndReport_ExternalIdAndExternalId(
                ReportSource.NAME, reportExternalId, photoExternalId);
    }
}
