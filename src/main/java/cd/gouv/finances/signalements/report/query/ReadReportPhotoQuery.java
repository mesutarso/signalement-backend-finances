package cd.gouv.finances.signalements.report.query;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.dto.PhotoFile;
import cd.gouv.finances.signalements.report.repository.ReportPhotoRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReadReportPhotoQuery {

    private final ReportPhotoRepository reportPhotoRepository;

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public PhotoFile execute(UUID reportId, UUID photoId) {
        var photo = reportPhotoRepository.findByReportAndId(reportId, photoId)
                .orElseThrow(() -> new ReportRejected("photo-absent", 404));
        return new PhotoFile(photo.getContentType(), photo.getContent());
    }
}
