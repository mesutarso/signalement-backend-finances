package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.dto.KeptResponse;
import cd.gouv.finances.signalements.report.dto.TaxpayerPageResponse;
import cd.gouv.finances.signalements.report.taxpayer.KeepTaxpayersCommand;
import cd.gouv.finances.signalements.report.taxpayer.ReadTaxpayersQuery;
import cd.gouv.finances.signalements.report.taxpayer.VerifyTaxpayerCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Taxpayers")
@SecurityRequirement(name = "auth")
public class TaxpayerController {

    private final ReadTaxpayersQuery readTaxpayersQuery;
    private final KeepTaxpayersCommand keepTaxpayersCommand;
    private final VerifyTaxpayerCommand verifyTaxpayerCommand;
    private final RecordAudit recordAudit;

    @GetMapping("/api/taxpayers")
    @Operation(summary = "Lists retained taxpayers without calling the tax registry")
    public TaxpayerPageResponse list(@RequestParam(defaultValue = "0") int page) {
        return readTaxpayersQuery.execute(page);
    }

    @PostMapping("/api/taxpayers")
    @Operation(summary = "Keeps each retained tax identifier once")
    public KeptResponse keep(@AuthenticationPrincipal AccountUserDetails account) {
        var created = keepTaxpayersCommand.execute();
        if (created > 0) {
            recordAudit.allowed(account.getAccountId(), "taxpayer-keep", "taxpayer", "kept");
        }
        return new KeptResponse(created);
    }

    @PostMapping("/api/taxpayers/{id}/verifications")
    @Operation(summary = "Asks the tax registry to confirm one taxpayer")
    public TaxpayerPageResponse verify(@PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails account) {
        verifyTaxpayerCommand.execute(id);
        recordAudit.allowed(account.getAccountId(), "taxpayer-verification", "taxpayer", id.toString());
        return readTaxpayersQuery.execute(0);
    }
}
