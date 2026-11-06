package cd.gouv.finances.signalements.report;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import cd.gouv.finances.signalements.report.repository.ReportImportRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledReportImport {
    private final ImportDispatcher dispatcher;
    private final ReportProperties properties;
    private final ReportImportRepository repository;
    private final Clock clock;
    @Value("${app.reports.schedule-enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${app.reports.cron:0 0 */3 * * *}", zone = "Africa/Kinshasa")
    public void scheduled() {
        if (enabled) launch("scheduled");
    }

    public void launch(String trigger) {
        if (properties.facno() == null || properties.facno().apiKey() == null || properties.facno().apiKey().isBlank()) return;
        var today = LocalDate.now(clock.withZone(ZoneId.of("Africa/Kinshasa")));
        var start = "startup".equals(trigger)
                ? LocalDate.of(2026, 1, 1)
                : repository.findFirstByStatusOrderByStartedAtDesc("completed")
                        .map(run -> run.getPeriodEnd() == null ? today.minusDays(3) : run.getPeriodEnd().minusDays(1))
                        .orElse(today.minusDays(3));
        if (start.isAfter(today)) start = today.minusDays(3);
        try {
            dispatcher.submit(start, today, trigger);
        } catch (ReportRejected exception) {
            log.info("Import not started: {}", exception.code());
        }
    }
}
