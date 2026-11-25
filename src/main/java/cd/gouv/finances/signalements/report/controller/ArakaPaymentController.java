package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.araka.ArakaProperties;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.payment.PaymentTransfers;
import cd.gouv.finances.signalements.report.payment.ReadPaymentJournal;
import cd.gouv.finances.signalements.report.payment.TransferPayments;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ArakaPaymentController {
    private final ArakaProperties properties;
    private final PaymentTransfers transfers;
    private final TransferPayments payments;
    private final RecordAudit audit;
    private final ReadPaymentJournal journal;

    public record TestBody(@NotNull UUID requestId, @NotBlank @Size(max = 40) String phone, @NotBlank @Size(max = 16) String operator) { }
    public record SendBody(@NotNull UUID requestId, @NotNull UUID lineId, @NotBlank @Size(max = 16) String operator) { }
    public record RewardSendBody(@NotNull UUID lineId, UUID previousTransferId,
            @NotBlank @Size(max = 16) String environment, @NotBlank @Size(max = 16) String operator) { }
    public record Status(String environment, boolean configured, List<PaymentTransfers.Transfer> transfers) { }

    @GetMapping("/api/payment-transfers")
    @PreAuthorize("hasAuthority('payment.read')")
    public Status status(@RequestParam(required = false) UUID statementId) { return new Status(properties.sandbox() ? "sandbox" : "production", properties.configured(), statementId == null ? transfers.recent() : transfers.forStatement(statementId)); }

    @GetMapping("/api/payment-transfers/journal")
    public ReadPaymentJournal.Journal journal(@RequestParam(required = false) String environment,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page) {
        return journal.read(environment, status, page);
    }

    @GetMapping("/api/payment-transfers/journal/history")
    public List<ReadPaymentJournal.Entry> history(@RequestParam String environment, @RequestParam UUID rewardId) {
        return journal.history(environment, rewardId);
    }

    @PostMapping("/api/payment-transfers/{id}/retry")
    public PaymentTransfers.Transfer retry(@PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails account) {
        return payments.retryFailed(id, account.getAccountId());
    }

    @PostMapping("/api/payment-transfers/tests")
    public PaymentTransfers.Transfer test(@Valid @RequestBody TestBody body, @AuthenticationPrincipal AccountUserDetails account) {
        var t = payments.test(body.phone(), body.operator(), body.requestId(), account.getAccountId());
        audit.allowed(account.getAccountId(), "payment-sandbox-test", "transfer", t.id().toString());
        return t;
    }

    @PostMapping("/api/payment-statements/{id}/transfers")
    public PaymentTransfers.Transfer send(@PathVariable UUID id, @Valid @RequestBody SendBody body, @AuthenticationPrincipal AccountUserDetails account) {
        var t = payments.send(id, body.lineId(), body.operator(), body.requestId(), account.getAccountId());
        audit.allowed(account.getAccountId(), "payment-transfer", "transfer", t.id().toString());
        return t;
    }

    @PostMapping("/api/rewards/{id}/transfers")
    public PaymentTransfers.Transfer sendReward(@PathVariable UUID id, @Valid @RequestBody RewardSendBody body,
            @AuthenticationPrincipal AccountUserDetails account) {
        var order = new PaymentTransfers.RewardOrder(body.lineId(), body.previousTransferId(), body.environment(), body.operator());
        return payments.sendReward(id, order, account.getAccountId());
    }

    @PostMapping("/api/payment-transfers/{id}/checks")
    public PaymentTransfers.Transfer check(@PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails account) {
        var t = payments.check(id);
        audit.allowed(account.getAccountId(), "payment-transfer-check", "transfer", t.id().toString());
        return t;
    }
}
