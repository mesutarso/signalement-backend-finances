package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.abuse.ReadAbuseIndicesQuery;
import cd.gouv.finances.signalements.report.abuse.RecordAbuseNoteCommand;
import cd.gouv.finances.signalements.report.dto.AbuseIndexPageResponse;
import cd.gouv.finances.signalements.report.dto.AbuseNoteBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Reports")
@SecurityRequirement(name = "auth")
public class AbuseIndexController {

    private final ReadAbuseIndicesQuery readAbuseIndicesQuery;
    private final RecordAbuseNoteCommand recordAbuseNoteCommand;
    private final RecordAudit recordAudit;

    @GetMapping("/api/abuse-indices")
    @Operation(summary = "Lists shared images that still need a human reading")
    public AbuseIndexPageResponse list(@RequestParam(defaultValue = "0") int page) {
        return readAbuseIndicesQuery.execute(page);
    }

    @PostMapping("/api/abuse-indices/{fingerprint}/notes")
    @Operation(summary = "Records a human reading of one shared image without treating it as fraud")
    public AbuseIndexPageResponse note(
            @PathVariable String fingerprint,
            @Valid @RequestBody AbuseNoteBody body,
            @RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal AccountUserDetails account) {
        recordAbuseNoteCommand.execute(fingerprint, body.decision(), body.reason(), account.getAccountId());
        recordAudit.allowed(account.getAccountId(), "abuse-note", "index", fingerprint);
        return readAbuseIndicesQuery.execute(page);
    }
}
