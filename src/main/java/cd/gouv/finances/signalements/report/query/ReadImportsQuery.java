package cd.gouv.finances.signalements.report.query;

import cd.gouv.finances.signalements.report.dto.ImportPageResponse;
import cd.gouv.finances.signalements.report.mapper.ReportMapper;
import cd.gouv.finances.signalements.report.repository.ReportImportRepository;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadImportsQuery {

    private static final ZoneId KINSHASA = ZoneId.of("Africa/Kinshasa");

    private final ReportImportRepository reportImportRepository;
    private final boolean scheduleEnabled;
    private final CronExpression cron;

    public ReadImportsQuery(
            ReportImportRepository reportImportRepository,
            @Value("${app.reports.schedule-enabled:true}") boolean scheduleEnabled,
            @Value("${app.reports.cron:0 0 */3 * * *}") String cron) {
        this.reportImportRepository = reportImportRepository;
        this.scheduleEnabled = scheduleEnabled;
        this.cron = CronExpression.parse(cron);
    }

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public ImportPageResponse execute(int page) {
        var runs = reportImportRepository.findAllByOrderByStartedAtDesc(PageRequest.of(Math.max(page, 0), 20));
        var next = scheduleEnabled ? cron.next(java.time.ZonedDateTime.now(KINSHASA)) : null;
        return new ImportPageResponse(
                runs.getContent().stream().map(ReportMapper::toImport).toList(),
                runs.getNumber(),
                runs.getSize(),
                runs.getTotalElements(),
                scheduleEnabled,
                reportImportRepository.existsByStatusIn(List.of("queued", "running")),
                next == null ? null : next.toInstant());
    }
}
