package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.dto.ReexaminationBody;
import cd.gouv.finances.signalements.report.dto.ReexaminationDecisionBody;
import cd.gouv.finances.signalements.report.dto.ReexaminationPageResponse;
import cd.gouv.finances.signalements.report.reexamination.DecideReexaminationCommand;
import cd.gouv.finances.signalements.report.reexamination.ReadReexaminationsQuery;
import cd.gouv.finances.signalements.report.reexamination.RecordReexaminationCommand;
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
@Tag(name = "Reexaminations")
@SecurityRequirement(name = "auth")
public class ReexaminationController {

    private final ReadReexaminationsQuery readReexaminationsQuery;
    private final RecordReexaminationCommand recordReexaminationCommand;
    private final DecideReexaminationCommand decideReexaminationCommand;
    private final RecordAudit recordAudit;

    @GetMapping("/api/reexaminations")
    @Operation(summary = "Lists reexamination requests without applying the thirty-day count")
    public ReexaminationPageResponse list(@RequestParam(defaultValue = "0") int page) {
        return readReexaminationsQuery.execute(page);
    }

    @PostMapping("/api/reexaminations")
    @Operation(summary = "Records a reexamination request received outside the citizen application")
    public ReexaminationPageResponse record(@Valid @RequestBody ReexaminationBody body, @AuthenticationPrincipal AccountUserDetails account) {
        var id = recordReexaminationCommand.execute(body.reference(), body.text(), body.receivedOn(), account.getAccountId());
        recordAudit.allowed(account.getAccountId(), "reexamination", "reexamination", id.toString());
        return readReexaminationsQuery.execute(0);
    }

    @PostMapping("/api/reexaminations/{id}/decisions")
    @Operation(summary = "Maintains or modifies a reexamination without changing points")
    public ReexaminationPageResponse decide(
            @PathVariable UUID id,
            @Valid @RequestBody ReexaminationDecisionBody body,
            @AuthenticationPrincipal AccountUserDetails account) {
        decideReexaminationCommand.execute(id, body.result(), body.comment(), account.getAccountId());
        recordAudit.allowed(account.getAccountId(), "reexamination-decision", "reexamination", id.toString());
        return readReexaminationsQuery.execute(0);
    }
}
