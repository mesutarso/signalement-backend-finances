package cd.gouv.finances.signalements.report.command;

import cd.gouv.finances.signalements.report.ImportDispatcher;
import java.util.UUID;
import cd.gouv.finances.signalements.report.dto.ImportResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RequestImportCommand {

    private final ImportDispatcher dispatcher;

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','ADMINISTRATOR')")
    public ImportResponse execute(LocalDate start, LocalDate end) {
        return dispatcher.submit(start, end, "manual");
    }
    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','ADMINISTRATOR')")
    public ImportResponse retry(UUID id) {
        return dispatcher.retry(id);
    }
}

