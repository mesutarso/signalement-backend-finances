package cd.gouv.finances.signalements.report.command;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.dto.ImportResponse;
import cd.gouv.finances.signalements.report.entity.ReportImport;
import cd.gouv.finances.signalements.report.mapper.ReportMapper;
import cd.gouv.finances.signalements.report.repository.ReportImportRepository;
import cd.gouv.finances.signalements.report.source.ReportSource;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImportReportsCommand {
    private static final int PAGE_SIZE = 10;
    private static final int PAGE_LIMIT = 1000;
    private final ReportSource reportSource;
    private final StoreReport storeReport;
    private final ReportImportRepository reportImportRepository;
    private final Clock clock;

    public ReportImport prepare(LocalDate start, LocalDate end, String trigger) {
        if (start == null || end == null || end.isBefore(start)) throw new ReportRejected("period", 400);
        var run = new ReportImport(UUID.randomUUID(), clock.instant(), null, "queued", 0, 0, 0, null);
        run.configure(start, end, trigger);
        try {
            run.heartbeat(clock.instant());
            return reportImportRepository.saveAndFlush(run);
        } catch (DataIntegrityViolationException exception) {
            throw new ReportRejected("import-running", 409);
        }
    }

    public ImportResponse execute(LocalDate start, LocalDate end) {
        return process(prepare(start, end, "manual"));
    }

    public ImportResponse process(ReportImport run) {
        int created = 0, skipped = 0, photos = 0;
        String error = null;
        try {
            run.progress("running", 0, 0, 0, null, null);
            reportImportRepository.saveAndFlush(run);
            boolean complete = false;
            for (int number = 1; number <= PAGE_LIMIT; number++) {
                var page = reportSource.read(run.getPeriodStart(), run.getPeriodEnd(), number, PAGE_SIZE);
                for (var report : page.reports()) {
                    var stored = storeReport.execute(report);
                    if (stored.created()) created++; else skipped++;
                    photos += stored.createdPhotos();
                    // Missing photos remain eligible on subsequent imports.
                    if (report.photos().stream().anyMatch(photo -> !photo.alreadyStored()
                            && (photo.content() == null || photo.content().length == 0))) {
                        error = "missing-photo";
                    }
                    run.progress("running", created, skipped, photos, error, null);
                    run.heartbeat(clock.instant());
                    reportImportRepository.saveAndFlush(run);
                }
                if (page.last()) { complete = true; break; }
            }
            if (!complete) error = "page-limit";
        } catch (ReportRejected exception) {
            error = exception.code();
        } catch (RuntimeException exception) {
            error = "import-error";
        }
        run.progress(error == null ? "completed" : "failed", created, skipped, photos, error, clock.instant());
        return ReportMapper.toImport(reportImportRepository.saveAndFlush(run));
    }
}
