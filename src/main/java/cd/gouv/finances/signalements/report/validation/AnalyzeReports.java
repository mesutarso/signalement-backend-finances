package cd.gouv.finances.signalements.report.validation;

import cd.gouv.finances.signalements.ocr.DocumentRejected;
import cd.gouv.finances.signalements.ocr.command.RequestReadingCommand;
import cd.gouv.finances.signalements.report.duplicate.PrepareDuplicateAlerts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.validation.analysis-enabled", havingValue = "true", matchIfMissing = true)
public class AnalyzeReports {
    private final AnalysisJobs jobs;
    private final AssessmentEvidence evidence;
    private final RequestReadingCommand reader;
    private final PrepareDuplicateAlerts duplicates;
    private final CompleteAnalysis complete;

    @Scheduled(fixedDelayString = "${app.validation.analysis-delay:2000}")
    public void run() {
        jobs.claim().ifPresent(job -> {
            try {
                for (var photo : evidence.photos(job.reportId())) {
                    if (!jobs.renew(job)) { jobs.failed(job, "analysis-superseded"); return; }
                    var existing = evidence.latest(photo.id());
                    if (existing != null && "READ".equals(existing.status())) continue;
                    try {
                        var reading = reader.execute(photo.id().toString(), evidence.content(photo.id()), photo.contentType());
                        if ("FAILED".equals(reading.status()) && !"not-configured".equals(reading.errorCode())) {
                            jobs.failed(job, reading.errorCode());
                            return;
                        }
                    } catch (DocumentRejected rejected) {
                        // A PDF or an unreadable attachment remains available for manual verification.
                    }
                }
                duplicates.execute(job.reportId());
                complete.execute(job);
            } catch (RuntimeException failure) {
                log.warn("Report analysis failed: {}", failure.getClass().getSimpleName());
                jobs.failed(job, "analysis-error");
            }
        });
    }
}
