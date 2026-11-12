package cd.gouv.finances.signalements.report.command;

import cd.gouv.finances.signalements.report.duplicate.PhotoFingerprint;
import cd.gouv.finances.signalements.report.entity.Report;
import cd.gouv.finances.signalements.report.entity.ReportPhoto;
import cd.gouv.finances.signalements.report.repository.ReportRepository;
import cd.gouv.finances.signalements.report.source.ReportSource;
import cd.gouv.finances.signalements.report.source.SourcedReport;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class StoreReport {

    private final ReportRepository reportRepository;
    private final Clock clock;
    private final cd.gouv.finances.signalements.report.validation.AnalysisJobs analysisJobs;

    @Transactional
    public Outcome execute(SourcedReport incoming) {
        analysisJobs.lock();
        var existing = reportRepository.findBySourceAndExternalId(ReportSource.NAME, incoming.externalId());
        if (existing.isEmpty()) {
            var report = new Report(
                    UUID.randomUUID(),
                    incoming.externalId(),
                    ReportSource.NAME,
                    incoming.externalId(),
                    incoming.reportedAt().toLocalDate(),
                    incoming.reportedAt(),
                    incoming.title(),
                    incoming.description(),
                    incoming.reporterName(),
                    incoming.reporterEmail(),
                    incoming.reporterPhone(),
                    incoming.reporterUserId(),
                    clock.instant());
            var photos = addPhotos(report, incoming);
            reportRepository.save(report);
            reportRepository.flush();
            analysisJobs.enqueue(report.getId(), true);
            return new Outcome(true, photos);
        }
        var photos = addPhotos(existing.get(), incoming);
        if (photos > 0) {
            reportRepository.flush();
            analysisJobs.enqueue(existing.get().getId(), true);
        }
        return new Outcome(false, photos);
    }

    private static int addPhotos(Report report, SourcedReport incoming) {
        var added = 0;
        for (var photo : incoming.photos()) {
            if (photo.alreadyStored() || photo.content() == null || photo.content().length == 0) {
                continue;
            }
            if (report.addPhoto(stamp(new ReportPhoto(
                    UUID.randomUUID(), report, photo.externalId(), photo.contentType(), photo.content())))) {
                added++;
            }
        }
        return added;
    }

    private static ReportPhoto stamp(ReportPhoto photo) {
        photo.stamp(PhotoFingerprint.of(photo.getContent()));
        return photo;
    }

    public record Outcome(boolean created, int createdPhotos) {
    }
}
