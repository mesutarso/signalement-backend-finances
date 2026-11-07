package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.abuse.ReadPrimeSuspensionsQuery;
import cd.gouv.finances.signalements.report.abuse.SuspendPrimeRightCommand;
import cd.gouv.finances.signalements.report.dto.PrimeSuspensionBody;
import cd.gouv.finances.signalements.report.dto.PrimeSuspensionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Reports")
@SecurityRequirement(name = "auth")
public class PrimeSuspensionController {

    private final ReadPrimeSuspensionsQuery readPrimeSuspensionsQuery;
    private final SuspendPrimeRightCommand suspendPrimeRightCommand;
    private final RecordAudit recordAudit;

    @GetMapping("/api/prime-suspensions")
    @Operation(summary = "Lists the latest prime-right decision for each declarant")
    public PrimeSuspensionResponse list() {
        return readPrimeSuspensionsQuery.execute();
    }

    @PostMapping("/api/prime-suspensions")
    @Operation(summary = "Suspends or restores the prime right of one declarant without merging accounts")
    public PrimeSuspensionResponse record(@RequestBody PrimeSuspensionBody body, @AuthenticationPrincipal AccountUserDetails account) {
        var reference = suspendPrimeRightCommand.execute(body.reference(), body.action(), body.reason(), account.getAccountId());
        recordAudit.allowed(account.getAccountId(), "prime-suspension", "suspension", reference);
        return readPrimeSuspensionsQuery.execute();
    }
}
