package cd.gouv.finances.signalements.report.duplicate;

import cd.gouv.finances.signalements.report.repository.ReportPhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class StampPhotoFingerprints {

    private final ReportPhotoRepository reportPhotoRepository;

    @Transactional
    public int stampReport(java.util.UUID reportId) {
        var photos = reportPhotoRepository.findByReport_IdAndFingerprintIsNull(reportId);
        return stamp(photos);
    }

    @Transactional
    public int stampBatch() {
        return stamp(reportPhotoRepository.findByFingerprintIsNull(PageRequest.of(0, 20)));
    }

    private int stamp(java.util.List<cd.gouv.finances.signalements.report.entity.ReportPhoto> photos) {
        for (var photo : photos) {
            photo.stamp(PhotoFingerprint.of(photo.getContent()));
        }
        if (!photos.isEmpty()) {
            reportPhotoRepository.saveAll(photos);
            reportPhotoRepository.flush();
        }
        return photos.size();
    }
}
