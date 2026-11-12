package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.dto.PayoutPageResponse;
import cd.gouv.finances.signalements.report.dto.RewardPageResponse;
import cd.gouv.finances.signalements.report.dto.PrimeReviewBody;
import cd.gouv.finances.signalements.report.dto.RewardResponse;
import cd.gouv.finances.signalements.report.prime.ReadPayoutNumbersQuery;
import cd.gouv.finances.signalements.report.prime.ReadRewardsQuery;
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
@Tag(name = "Récompenses")
@SecurityRequirement(name = "auth")
public class RewardController {

    private final ReadRewardsQuery readRewardsQuery;
    private final ReadPayoutNumbersQuery readPayoutNumbersQuery;
    private final ReviewPrimeCommand reviewPrimeCommand;
    private final RecordAudit recordAudit;

    @GetMapping("/api/payout-numbers")
    @Operation(summary = "Lists the recorded declarant payout numbers without starting a payment")
    public PayoutPageResponse payoutNumbers(@RequestParam(defaultValue = "0") int page) {
        return readPayoutNumbersQuery.execute(page);
    }

    @GetMapping("/api/rewards")
    @Operation(summary = "Lists per-report rewards without starting a payment")
    public RewardPageResponse list(@RequestParam(defaultValue = "0") int page) {
        return readRewardsQuery.execute(page);
    }

    @PostMapping("/api/rewards/{id}/reviews")
    @Operation(summary = "Approves or returns a per-report reward without starting a payment")
    public RewardResponse review(
            @PathVariable UUID id,
            @Valid @RequestBody PrimeReviewBody body,
            @AuthenticationPrincipal AccountUserDetails account) {
        var outcome = reviewPrimeCommand.execute(id, body.result(), body.reason(), account.getAccountId());
        recordAudit.allowed(account.getAccountId(), outcome.action(), "reward", id.toString());
        if (outcome.payoutId() != null) {
            recordAudit.allowed(account.getAccountId(), "payout-record", "payout", outcome.payoutId().toString());
        }
        return readRewardsQuery.executeOne(id);
    }
}
