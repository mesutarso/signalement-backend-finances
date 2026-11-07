package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.dto.SupervisionBody;
import cd.gouv.finances.signalements.report.dto.SupervisionPageResponse;
import cd.gouv.finances.signalements.report.supervision.ReadSupervisionQuery;
import cd.gouv.finances.signalements.report.supervision.RecordSupervisionCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
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
public class SupervisionController {

    private final ReadSupervisionQuery readSupervisionQuery;
    private final RecordSupervisionCommand recordSupervisionCommand;
    private final RecordAudit recordAudit;

    @GetMapping("/api/supervision")
    @Operation(summary = "Lists qualified reports for a human quality review")
    public SupervisionPageResponse list(
            @RequestParam(defaultValue = "open") String status,
            @RequestParam(defaultValue = "0") int page) {
        return readSupervisionQuery.execute(status, page);
    }

    @PostMapping("/api/reports/{id}/supervision")
    @Operation(summary = "Records a supervision result without removing the qualification")
    public SupervisionPageResponse review(
            @PathVariable UUID id,
            @Valid @RequestBody SupervisionBody body,
            @RequestParam(defaultValue = "open") String status,
            @RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal AccountUserDetails account) {
        var created = recordSupervisionCommand.execute(id, body.result(), body.reason(), account.getAccountId());
        if (created) {
            recordAudit.allowed(account.getAccountId(), "supervision", "report", id.toString());
        }
        return readSupervisionQuery.execute(status, page);
    }
}
