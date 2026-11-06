package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.report.command.RequestImportCommand;
import cd.gouv.finances.signalements.report.dto.ImportPageResponse;
import cd.gouv.finances.signalements.report.dto.ImportRequest;
import cd.gouv.finances.signalements.report.dto.ImportResponse;
import cd.gouv.finances.signalements.report.query.ReadImportsQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Report imports")
@SecurityRequirement(name = "auth")
public class ReportImportController {

    private final RequestImportCommand requestImportCommand;
    private final ReadImportsQuery readImportsQuery;

    @GetMapping("/api/report-imports")
    @Operation(summary = "Lists report imports")
    public ImportPageResponse list(@RequestParam(defaultValue = "0") int page) {
        return readImportsQuery.execute(page);
    }

    @PostMapping("/api/report-imports/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Retries one report import")
    public ImportResponse retry(@PathVariable UUID id) {
        return requestImportCommand.retry(id);
    }

    @PostMapping("/api/report-imports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Imports reports for a period without duplicating them")
    public ImportResponse create(@Valid @RequestBody ImportRequest request) {
        return requestImportCommand.execute(request.start(), request.end());
    }
}
