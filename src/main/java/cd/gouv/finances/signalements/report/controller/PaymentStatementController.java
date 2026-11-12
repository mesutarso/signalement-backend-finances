package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.dto.PaymentAttemptBody;
import cd.gouv.finances.signalements.report.dto.PaymentDecisionBody;
import cd.gouv.finances.signalements.report.dto.PaymentPageResponse;
import cd.gouv.finances.signalements.report.dto.PaymentStatementResponse;
import cd.gouv.finances.signalements.report.payment.DecidePaymentStatementCommand;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.payment.PreparePaymentStatementCommand;
import cd.gouv.finances.signalements.report.payment.ReadPaymentStatementsQuery;
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
@Tag(name = "Payment statements")
@SecurityRequirement(name = "auth")
public class PaymentStatementController {

    private final ReadPaymentStatementsQuery readPaymentStatementsQuery;
    private final PreparePaymentStatementCommand preparePaymentStatementCommand;
    private final DecidePaymentStatementCommand decidePaymentStatementCommand;
    private final RecordAudit recordAudit;

    @GetMapping("/api/payment-statements")
    @Operation(summary = "Lists payment statements without starting a payment")
    public PaymentPageResponse list(@RequestParam(defaultValue = "0") int page) {
        return readPaymentStatementsQuery.execute(page);
    }

    @GetMapping("/api/payment-statements/{id}")
    @Operation(summary = "Returns one payment statement without starting a payment")
    public PaymentStatementResponse one(@PathVariable UUID id) {
        return readPaymentStatementsQuery.executeOne(id);
    }

    @PostMapping("/api/payment-statements")
    @Operation(summary = "Prepares a payment statement from approved primes")
    public PaymentStatementResponse prepare(@AuthenticationPrincipal AccountUserDetails account) {
        var prepared = preparePaymentStatementCommand.execute(account.getAccountId());
        recordAudit.allowed(account.getAccountId(), "payment-statement", "payment", prepared.id().toString());
        return readPaymentStatementsQuery.executeOne(prepared.id());
    }

    @PostMapping("/api/payment-statements/{id}/controls")
    @Operation(summary = "Records the supervisor control of a payment statement")
    public PaymentStatementResponse control(
            @PathVariable UUID id,
            @Valid @RequestBody PaymentDecisionBody body,
            @AuthenticationPrincipal AccountUserDetails account) {
        return decide(id, account, decidePaymentStatementCommand.control(id, body.result(), body.reason(), account.getAccountId()));
    }

    @PostMapping("/api/payment-statements/{id}/visas")
    @Operation(summary = "Records the fiscal cell visa of a payment statement")
    public PaymentStatementResponse visa(
            @PathVariable UUID id,
            @Valid @RequestBody PaymentDecisionBody body,
            @AuthenticationPrincipal AccountUserDetails account) {
        return decide(id, account, decidePaymentStatementCommand.visa(id, body.result(), body.reason(), account.getAccountId()));
    }

    @PostMapping("/api/payment-statements/{id}/authorizations")
    @Operation(summary = "Records the cabinet authorization of a payment statement")
    public PaymentStatementResponse authorize(
            @PathVariable UUID id,
            @Valid @RequestBody PaymentDecisionBody body,
            @AuthenticationPrincipal AccountUserDetails account) {
        return decide(id, account, decidePaymentStatementCommand.authorize(id, body.result(), body.reason(), account.getAccountId()));
    }

    @PostMapping("/api/payment-statements/{id}/attempts")
    @Operation(summary = "Retired simulation endpoint; results can only come from Araka")
    public PaymentStatementResponse attempt(
            @PathVariable UUID id,
            @Valid @RequestBody PaymentAttemptBody body,
            @AuthenticationPrincipal AccountUserDetails account) {
        throw new ReportRejected("payment-simulation-retired", 410);
    }

    private PaymentStatementResponse decide(UUID id, AccountUserDetails account, String action) {
        recordAudit.allowed(account.getAccountId(), action, "payment", id.toString());
        return readPaymentStatementsQuery.executeOne(id);
    }
}
