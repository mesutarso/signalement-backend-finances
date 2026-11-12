package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.araka.ArakaProperties;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.payment.PaymentTransfers;
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

    public record TestBody(@NotNull UUID requestId, @NotBlank @Size(max = 40) String phone, @NotBlank @Size(max = 16) String operator) { }
    public record SendBody(@NotNull UUID requestId, @NotNull UUID lineId, @NotBlank @Size(max = 16) String operator) { }
    public record Status(String environment, boolean configured, List<PaymentTransfers.Transfer> transfers) { }

    @GetMapping("/api/payment-transfers")
    @PreAuthorize("hasAnyRole('SUPERVISOR','AUDITOR','ADMINISTRATOR','CELLULE','CABINET','PAYMASTER')")
    public Status status(@RequestParam(required = false) UUID statementId) { return new Status(properties.sandbox() ? "sandbox" : "production", properties.configured(), statementId == null ? transfers.recent() : transfers.forStatement(statementId)); }

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

    @PostMapping("/api/payment-transfers/{id}/checks")
    public PaymentTransfers.Transfer check(@PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails account) {
        var t = payments.check(id);
        audit.allowed(account.getAccountId(), "payment-transfer-check", "transfer", t.id().toString());
        return t;
    }
}
