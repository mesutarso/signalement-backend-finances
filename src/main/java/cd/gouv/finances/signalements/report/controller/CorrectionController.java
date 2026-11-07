package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.correction.FallConfirmationCommand;
import cd.gouv.finances.signalements.report.correction.ReadCorrectionsQuery;
import cd.gouv.finances.signalements.report.dto.CorrectionPageResponse;
import cd.gouv.finances.signalements.report.dto.ReferenceReasonBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Corrections")
@SecurityRequirement(name = "auth")
public class CorrectionController {

    private final ReadCorrectionsQuery readCorrectionsQuery;
    private final FallConfirmationCommand fallConfirmationCommand;
    private final RecordAudit recordAudit;

    @GetMapping("/api/corrections")
    @Operation(summary = "Lists traced corrections of fallen confirmations")
    public CorrectionPageResponse list(@RequestParam(defaultValue = "0") int page) {
        return readCorrectionsQuery.execute(page);
    }

    @PostMapping("/api/corrections")
    @Operation(summary = "Records that a confirmation fell and reverses its points")
    public CorrectionPageResponse fall(@Valid @RequestBody ReferenceReasonBody body, @AuthenticationPrincipal AccountUserDetails account) {
        var id = fallConfirmationCommand.execute(body.reference(), body.reason(), account.getAccountId());
        recordAudit.allowed(account.getAccountId(), "confirmation-fall", "correction", id.toString());
        return readCorrectionsQuery.execute(0);
    }
}
