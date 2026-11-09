package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.Role;
import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.command.AssignReportCommand;
import cd.gouv.finances.signalements.report.command.DecideDuplicateCommand;
import cd.gouv.finances.signalements.report.command.RecordQualificationCommand;
import cd.gouv.finances.signalements.report.duplicate.PrepareDuplicateAlerts;
import cd.gouv.finances.signalements.report.dto.DuplicateDecisionBody;
import cd.gouv.finances.signalements.report.dto.QualificationBody;
import cd.gouv.finances.signalements.report.command.LinkPhotoRequestCommand;
import cd.gouv.finances.signalements.report.command.RequestPhotoCommand;
import cd.gouv.finances.signalements.report.command.RequestPhotoReadingCommand;
import cd.gouv.finances.signalements.report.dto.PhotoLinkBody;
import cd.gouv.finances.signalements.report.dto.PhotoReadingResponse;
import cd.gouv.finances.signalements.report.dto.PhotoRequestBody;
import cd.gouv.finances.signalements.report.dto.PhotoRequestResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
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
    private final RequestPhotoReadingCommand requestPhotoReadingCommand;
    private final RequestPhotoCommand requestPhotoCommand;
    private final LinkPhotoRequestCommand linkPhotoRequestCommand;
    private final PrepareDuplicateAlerts prepareDuplicateAlerts;
    private final DecideDuplicateCommand decideDuplicateCommand;
    private final RecordQualificationCommand recordQualificationCommand;
    private final RecordAudit recordAudit;

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
        recordAudit.allowed(account.getAccountId(), "assignment", "report", id.toString());
        prepareDuplicateAlerts.execute(id);
        return readReportQuery.execute(id, account.getRole() != Role.AUDITOR);
    }

    @GetMapping("/api/reports/{id}")
    @Operation(summary = "Returns one report and its photos")
    public ReportResponse one(@PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails account) {
        prepareDuplicateAlerts.execute(id);
        return readReportQuery.execute(id, account.getRole() != Role.AUDITOR);
    }

    @PostMapping("/api/reports/{id}/duplicates/{alertId}")
    @Operation(summary = "Confirms or dismisses one duplicate alert")
    public ReportResponse decideDuplicate(
            @PathVariable UUID id,
            @PathVariable UUID alertId,
            @Valid @RequestBody DuplicateDecisionBody body,
            @AuthenticationPrincipal AccountUserDetails account) {
        decideDuplicateCommand.execute(id, alertId, body.decision(), body.reason(), account.getAccountId());
        recordAudit.allowed(account.getAccountId(), "duplicate", "report", id.toString());
        prepareDuplicateAlerts.execute(id);
        return readReportQuery.execute(id, account.getRole() != Role.AUDITOR);
    }

    @PostMapping("/api/reports/{id}/qualifications")
    @Operation(summary = "Records a sufficient-proof qualification without awarding points")
    public ReportResponse qualify(
            @PathVariable UUID id,
            @Valid @RequestBody QualificationBody body,
            @AuthenticationPrincipal AccountUserDetails account) {
        recordQualificationCommand.execute(id, new RecordQualificationCommand.QualificationRequest(
                body.decision(),
                body.finding(),
                body.proof(),
                body.justification(),
                body.sellerName(),
                body.sellerTaxId(),
                body.issuedOn(),
                body.amount(),
                body.documentNumber(),
                body.code(),
                body.missingElements(),
                body.dgiReference()), account.getAccountId());
        recordAudit.allowed(account.getAccountId(), "qualification", "report", id.toString());
        return readReportQuery.execute(id, account.getRole() != Role.AUDITOR);
    }

    @PostMapping("/api/reports/{id}/photos/{photoId}/readings")
    @Operation(summary = "Reads one report photo")
    public PhotoReadingResponse readPhoto(
            @PathVariable UUID id, @PathVariable UUID photoId,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return requestPhotoReadingCommand.execute(id, photoId, refresh);
    }

    @PostMapping("/api/reports/{id}/photos/{photoId}/requests")
    @Operation(summary = "Asks for another photo without changing the original report")
    public PhotoRequestResponse requestPhoto(
            @PathVariable UUID id,
            @PathVariable UUID photoId,
            @Valid @RequestBody PhotoRequestBody body,
            @AuthenticationPrincipal AccountUserDetails account) {
        var response = requestPhotoCommand.execute(id, photoId, body.reason(), account.getAccountId());
        recordAudit.allowed(account.getAccountId(), "photo-request", "report", id.toString());
        return response;
    }

    @PostMapping("/api/reports/{id}/photo-requests/{requestId}/links")
    @Operation(summary = "Links a later report as the replacement photo")
    public PhotoRequestResponse linkPhoto(
            @PathVariable UUID id,
            @PathVariable UUID requestId,
            @Valid @RequestBody PhotoLinkBody body,
            @AuthenticationPrincipal AccountUserDetails account) {
        var response = linkPhotoRequestCommand.execute(id, requestId, body.referenceCode());
        recordAudit.allowed(account.getAccountId(), "photo-link", "report", id.toString());
        return response;
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
