package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.Role;
import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.report.command.AssignReportCommand;
import cd.gouv.finances.signalements.report.dto.AssignRequest;
import cd.gouv.finances.signalements.report.dto.AssigneeResponse;
import cd.gouv.finances.signalements.report.dto.ReportPageResponse;
import cd.gouv.finances.signalements.report.dto.ReportResponse;
import cd.gouv.finances.signalements.report.query.ReadAssigneesQuery;
import cd.gouv.finances.signalements.report.query.ReadReportPhotoQuery;
import cd.gouv.finances.signalements.report.query.ReadReportQuery;
import cd.gouv.finances.signalements.report.query.ReadReportsQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Reports")
@SecurityRequirement(name = "auth")
public class ReportController {

    private final ReadReportsQuery readReportsQuery;
    private final ReadReportQuery readReportQuery;
    private final ReadReportPhotoQuery readReportPhotoQuery;
    private final ReadAssigneesQuery readAssigneesQuery;
    private final AssignReportCommand assignReportCommand;

    @GetMapping("/api/reports")
    @Operation(summary = "Lists the work queue")
    public ReportPageResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(defaultValue = "false") boolean unassigned,
            @AuthenticationPrincipal AccountUserDetails account) {
        return readReportsQuery.execute(
                page,
                size,
                account != null && account.getRole() != Role.AUDITOR,
                new ReadReportsQuery.ReportSearch(q, state, priority, assigneeId, unassigned));
    }

    @GetMapping("/api/reports/assignees")
    @Operation(summary = "Lists agents who can receive a report")
    public List<AssigneeResponse> assignees() {
        return readAssigneesQuery.execute();
    }

    @PutMapping("/api/reports/{id}/assignment")
    @Operation(summary = "Assigns or reassigns a report")
    public ReportResponse assign(
            @PathVariable UUID id,
            @Valid @RequestBody AssignRequest request,
            @AuthenticationPrincipal AccountUserDetails account) {
        assignReportCommand.execute(id, request.version(), request.assigneeId(), request.priority(), account.getAccountId());
        return readReportQuery.execute(id, account.getRole() != Role.AUDITOR);
    }

    @GetMapping("/api/reports/{id}")
    @Operation(summary = "Returns one report and its photos")
    public ReportResponse one(@PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails account) {
        return readReportQuery.execute(id, account.getRole() != Role.AUDITOR);
    }

    @GetMapping("/api/reports/{id}/photos/{photoId}")
    @Operation(summary = "Returns one report photo")
    public ResponseEntity<byte[]> photo(@PathVariable UUID id, @PathVariable UUID photoId) {
        var file = readReportPhotoQuery.execute(id, photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }
}
