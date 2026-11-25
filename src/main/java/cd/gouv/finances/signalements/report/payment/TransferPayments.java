package cd.gouv.finances.signalements.report.payment;

import cd.gouv.finances.signalements.araka.ArakaGateway;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransferPayments {
    private final PaymentTransfers transfers;
    private final ArakaGateway gateway;
    private final RecordAudit audit;

    @PreAuthorize("hasAuthority('payment.execute')")
    public PaymentTransfers.Transfer send(UUID statement, UUID line, String operator, UUID request, UUID actor) {
        return submit(transfers.reserveLine(statement, line, operator, request, actor));
    }

    @PreAuthorize("hasAuthority('payment.execute')")
    public PaymentTransfers.Transfer sendReward(UUID rewardId, PaymentTransfers.RewardOrder order, UUID actor) {
        var claim = transfers.reserveReward(rewardId, order, actor);
        var transfer = submit(claim);
        if (claim.submit()) audit.allowed(actor, "payment-transfer", "transfer", transfer.id().toString());
        return transfer;
    }

    @PreAuthorize("hasAuthority('payment.execute')")
    public PaymentTransfers.Transfer retryFailed(UUID id, UUID actor) {
        var claim = transfers.reserveFailed(id, actor);
        var transfer = submit(claim);
        if (claim.submit()) audit.allowed(actor, "payment-transfer-retry", "transfer", transfer.id().toString());
        return transfer;
    }

    @PreAuthorize("hasAuthority('payment.test')")
    public PaymentTransfers.Transfer test(String phone, String operator, UUID request, UUID actor) {
        return submit(transfers.reserveTest(phone, operator, request, actor));
    }

    private PaymentTransfers.Transfer submit(PaymentTransfers.Claim claim) {
        var t = claim.transfer();
        if (!claim.submit()) return t;
        // The reservation is committed before any remote side effect; there is no automatic POST retry.
        return transfers.submitted(t.id(), gateway.send(t.reference(), t.amount(), t.wallet(), t.provider()));
    }

    @PreAuthorize("hasAuthority('payment.check')")
    public PaymentTransfers.Transfer check(UUID id) { return reconcile(id); }

    private PaymentTransfers.Transfer reconcile(UUID id) {
        var check = transfers.claimCheck(id);
        var t = check.transfer();
        if (check.lease() == null) return t;
        return transfers.checked(check, gateway.lookup(t.reference(), t.transactionId()));
    }

    @Scheduled(fixedDelayString = "${app.payments.araka.reconcile-delay:15000}")
    public void reconcileDue() {
        for (var id : transfers.exceptionDue()) {
            try {
                var claim = transfers.claimException(id);
                if (claim != null) submit(claim);
            } catch (RuntimeException error) {
                log.warn("Exceptional payment deferred: {}", error.getClass().getSimpleName());
            }
        }
        for (var id : transfers.due()) {
            try { reconcile(id); }
            catch (RuntimeException error) { log.warn("Payment reconciliation deferred: {}", error.getClass().getSimpleName()); }
        }
    }
}
