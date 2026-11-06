package cd.gouv.finances.signalements.report;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportImportRunner implements ApplicationRunner {
    private final ScheduledReportImport scheduledImport;
    private final ReportProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (properties.importOnStartup()) scheduledImport.launch("startup");
    }
}
