package cd.gouv.finances.signalements.report;

import org.springframework.scheduling.annotation.Scheduled;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import cd.gouv.finances.signalements.report.command.ImportReportsCommand;
import cd.gouv.finances.signalements.report.dto.ImportResponse;
import cd.gouv.finances.signalements.report.mapper.ReportMapper;
import cd.gouv.finances.signalements.report.repository.ReportImportRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class ImportDispatcher {
    private final ImportReportsCommand command;
    private final ReportImportRepository repository;
    private final TaskExecutor executor;
    private final Clock clock;
    private final AtomicReference<UUID> activeId = new AtomicReference<>();

    public ImportDispatcher(ImportReportsCommand command, ReportImportRepository repository,
            @Qualifier("reportImportExecutor") TaskExecutor executor, Clock clock) {
        this.command = command;
        this.repository = repository;
        this.executor = executor;
        this.clock = clock;
    }

    public ImportResponse submit(LocalDate start, LocalDate end, String trigger) {
        var run = command.prepare(start, end, trigger);
        var response = ReportMapper.toImport(run);
        try {
            executor.execute(() -> {
                activeId.set(run.getId());
                try { command.process(run); } finally { activeId.set(null); }
            });
        } catch (RuntimeException exception) {
            run.progress("failed", 0, 0, 0, "dispatch-error", clock.instant());
            repository.saveAndFlush(run);
            throw new ReportRejected("dispatch-error", 503);
        }
        return response;
    }

    @Scheduled(fixedDelay = 60000)
    public void heartbeat() {
        var now = clock.instant();
        var id = activeId.get();
        if (id != null) repository.heartbeat(id, now);
        repository.failInterrupted(now.minus(Duration.ofMinutes(10)), now);
    }

    public ImportResponse retry(UUID id) {
        var run = repository.findById(id).orElseThrow(() -> new ReportRejected("absent", 404));
        if (run.getPeriodStart() == null || run.getPeriodEnd() == null) throw new ReportRejected("period", 400);
        return submit(run.getPeriodStart(), run.getPeriodEnd(), "retry");
    }
}
