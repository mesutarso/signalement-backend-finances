package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.dto.PayoutPageResponse;
import cd.gouv.finances.signalements.report.dto.PrimeAccountResponse;
import cd.gouv.finances.signalements.report.dto.PrimePageResponse;
import cd.gouv.finances.signalements.report.dto.PrimeReviewBody;
import cd.gouv.finances.signalements.report.dto.PrimeTierResponse;
import cd.gouv.finances.signalements.report.prime.ReadPayoutNumbersQuery;
import cd.gouv.finances.signalements.report.prime.ReadPrimesQuery;
import cd.gouv.finances.signalements.report.prime.ReviewPrimeCommand;
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
@Tag(name = "Primes")
@SecurityRequirement(name = "auth")
public class PrimeController {

    private final ReadPrimesQuery readPrimesQuery;
    private final ReadPayoutNumbersQuery readPayoutNumbersQuery;
    private final ReviewPrimeCommand reviewPrimeCommand;
    private final RecordAudit recordAudit;

    @GetMapping("/api/payout-numbers")
    @Operation(summary = "Lists the recorded declarant payout numbers without starting a payment")
    public PayoutPageResponse payoutNumbers(@RequestParam(defaultValue = "0") int page) {
        return readPayoutNumbersQuery.execute(page);
    }

    @GetMapping("/api/primes")
    @Operation(summary = "Lists proposed primes without starting a payment")
    public PrimePageResponse list(@RequestParam(defaultValue = "0") int page) {
        return readPrimesQuery.execute(page);
    }

    @GetMapping("/api/reports/{id}/prime")
    @Operation(summary = "Shows the declarant prime account of one report")
    public PrimeAccountResponse report(@PathVariable UUID id) {
        return readPrimesQuery.executeForReport(id);
    }

    @PostMapping("/api/primes/{id}/reviews")
    @Operation(summary = "Approves or returns a proposed prime without starting a payment")
    public PrimeTierResponse review(
            @PathVariable UUID id,
            @Valid @RequestBody PrimeReviewBody body,
            @AuthenticationPrincipal AccountUserDetails account) {
        var outcome = reviewPrimeCommand.execute(id, body.result(), body.reason(), account.getAccountId());
        recordAudit.allowed(account.getAccountId(), outcome.action(), "prime", id.toString());
        if (outcome.payoutId() != null) {
            recordAudit.allowed(account.getAccountId(), "payout-record", "payout", outcome.payoutId().toString());
        }
        return readPrimesQuery.executeOne(id);
    }
}
