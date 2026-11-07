package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.dto.LetterDecisionBody;
import cd.gouv.finances.signalements.report.dto.LetterDetailResponse;
import cd.gouv.finances.signalements.report.dto.LetterPageResponse;
import cd.gouv.finances.signalements.report.dto.LetterPreparedResponse;
import cd.gouv.finances.signalements.report.letter.DecideLetterCommand;
import cd.gouv.finances.signalements.report.letter.PrepareLetterCommand;
import cd.gouv.finances.signalements.report.letter.ReadLetterAnnexQuery;
import cd.gouv.finances.signalements.report.letter.ReadLetterQuery;
import cd.gouv.finances.signalements.report.letter.ReadLetterResponseFileQuery;
import cd.gouv.finances.signalements.report.letter.ReadLettersQuery;
import cd.gouv.finances.signalements.report.letter.RecordLetterResponseCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@Tag(name = "Reports")
@SecurityRequirement(name = "auth")
public class LetterController {

    private final ReadLettersQuery readLettersQuery;
    private final ReadLetterQuery readLetterQuery;
    private final ReadLetterAnnexQuery readLetterAnnexQuery;
    private final PrepareLetterCommand prepareLetterCommand;
    private final DecideLetterCommand decideLetterCommand;
    private final RecordLetterResponseCommand recordLetterResponseCommand;
    private final ReadLetterResponseFileQuery readLetterResponseFileQuery;
    private final RecordAudit recordAudit;

    @GetMapping("/api/letters")
    @Operation(summary = "Lists prepared letters without sending them")
    public LetterPageResponse list(
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "0") int page) {
        return readLettersQuery.execute(status, page);
    }

    @GetMapping("/api/letters/{id}")
    @Operation(summary = "Reads one letter and its annex lines")
    public LetterDetailResponse one(@PathVariable UUID id) {
        return readLetterQuery.execute(id);
    }

    @GetMapping(value = "/api/letters/{id}/annexes", produces = "text/csv")
    @Operation(summary = "Downloads a company or invoice annex as a spreadsheet")
    public ResponseEntity<byte[]> annex(@PathVariable UUID id, @RequestParam String annex) {
        var name = "company".equals(annex) ? "entreprises.csv" : "factures.csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(readLetterAnnexQuery.execute(id, annex));
    }

    @PostMapping("/api/letters")
    @Operation(summary = "Prepares this week's letter from retained findings that share a tax identifier")
    public LetterPreparedResponse prepare(@AuthenticationPrincipal AccountUserDetails account) {
        var prepared = prepareLetterCommand.execute(account.getAccountId());
        if (prepared.created()) {
            recordAudit.allowed(account.getAccountId(), "letter", "letter", prepared.id().toString());
        }
        return new LetterPreparedResponse(prepared.id(), prepared.created());
    }

    @GetMapping("/api/letters/{id}/responses/{responseId}")
    @Operation(summary = "Downloads the document attached to a received response")
    public ResponseEntity<byte[]> responseFile(@PathVariable UUID id, @PathVariable UUID responseId) {
        var file = readLetterResponseFileQuery.execute(id, responseId);
        var name = switch (file.contentType()) {
            case "application/pdf" -> "reponse.pdf";
            case "image/png" -> "reponse.png";
            case "image/webp" -> "reponse.webp";
            default -> "reponse.jpg";
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @PostMapping(value = "/api/letters/{id}/responses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Records a received response on a validated letter without sending it or changing points")
    public LetterDetailResponse respond(
            @PathVariable UUID id,
            @RequestParam String receivedOn,
            @RequestParam String service,
            @RequestParam String reference,
            @RequestParam String conclusion,
            @RequestParam MultipartFile document,
            @AuthenticationPrincipal AccountUserDetails account) {
        var created = recordLetterResponseCommand.execute(id, receivedOn, service, reference, conclusion, bytes(document), account.getAccountId());
        if (created) {
            recordAudit.allowed(account.getAccountId(), "letter-response", "letter", id.toString());
        }
        return readLetterQuery.execute(id);
    }

    private static byte[] bytes(MultipartFile document) {
        try {
            return document == null ? null : document.getBytes();
        } catch (IOException exception) {
            throw new ReportRejected("response-document", 400);
        }
    }

    @PostMapping("/api/letters/{id}/decisions")
    @Operation(summary = "Validates or returns a draft letter without deleting its lines or sending it")
    public LetterDetailResponse decide(
            @PathVariable UUID id,
            @Valid @RequestBody LetterDecisionBody body,
            @AuthenticationPrincipal AccountUserDetails account) {
        var decided = decideLetterCommand.execute(id, body.result(), body.recipient(), body.reason(), account.getAccountId());
        if (decided) {
            recordAudit.allowed(account.getAccountId(), "letter-decision", "letter", id.toString());
        }
        return readLetterQuery.execute(id);
    }
}
