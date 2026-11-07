package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.dto.PaymentSampleBody;
import cd.gouv.finances.signalements.report.dto.PaymentSamplePageResponse;
import cd.gouv.finances.signalements.report.dto.PaymentSampleReviewBody;
import cd.gouv.finances.signalements.report.sample.PreparePaymentSampleCommand;
import cd.gouv.finances.signalements.report.sample.ReadPaymentSamplesQuery;
import cd.gouv.finances.signalements.report.sample.ReviewPaymentSampleCommand;
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
@Tag(name = "Payment samples")
@SecurityRequirement(name = "auth")
public class PaymentSampleController {

    private final ReadPaymentSamplesQuery readPaymentSamplesQuery;
    private final PreparePaymentSampleCommand preparePaymentSampleCommand;
    private final ReviewPaymentSampleCommand reviewPaymentSampleCommand;
    private final RecordAudit recordAudit;

    @GetMapping("/api/payment-samples")
    @Operation(summary = "Lists the samples of paid primes")
    public PaymentSamplePageResponse list(@RequestParam(defaultValue = "0") int page) {
        return readPaymentSamplesQuery.execute(page);
    }

    @GetMapping("/api/payment-samples/{id}")
    @Operation(summary = "Returns one sample of paid primes")
    public PaymentSamplePageResponse.Detail one(@PathVariable UUID id) {
        return readPaymentSamplesQuery.executeOne(id);
    }

    @PostMapping("/api/payment-samples")
    @Operation(summary = "Selects five percent of the paid primes in a period")
    public PaymentSamplePageResponse.Detail prepare(@Valid @RequestBody PaymentSampleBody body, @AuthenticationPrincipal AccountUserDetails account) {
        var id = preparePaymentSampleCommand.execute(body.periodStart(), body.periodEnd(), account.getAccountId());
        recordAudit.allowed(account.getAccountId(), "payment-sample", "payment-sample", id.toString());
        return readPaymentSamplesQuery.executeOne(id);
    }

    @PostMapping("/api/payment-samples/lines/{id}/reviews")
    @Operation(summary = "Records the review of one sampled payment")
    public PaymentSamplePageResponse.Detail review(
            @PathVariable UUID id,
            @Valid @RequestBody PaymentSampleReviewBody body,
            @AuthenticationPrincipal AccountUserDetails account) {
        reviewPaymentSampleCommand.execute(id, body.result(), body.reason(), account.getAccountId());
        var sampleId = readPaymentSamplesQuery.sampleOf(id);
        recordAudit.allowed(account.getAccountId(), "payment-sample-review", "payment-sample", sampleId.toString());
        return readPaymentSamplesQuery.executeOne(sampleId);
    }
}
