package cd.gouv.finances.signalements.report.payment;

import cd.gouv.finances.signalements.araka.ArakaGateway;
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

    @PreAuthorize("hasRole('PAYMASTER')")
    public PaymentTransfers.Transfer send(UUID statement, UUID line, String operator, UUID request, UUID actor) {
        return submit(transfers.reserveLine(statement, line, operator, request, actor));
    }

    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public PaymentTransfers.Transfer test(String phone, String operator, UUID request, UUID actor) {
        return submit(transfers.reserveTest(phone, operator, request, actor));
    }

    private PaymentTransfers.Transfer submit(PaymentTransfers.Claim claim) {
        var t = claim.transfer();
        if (!claim.submit()) return t;
        // The reservation is committed before any remote side effect; there is no automatic POST retry.
        return transfers.submitted(t.id(), gateway.send(t.reference(), t.amount(), t.wallet(), t.provider()));
    }

    @PreAuthorize("hasAnyRole('PAYMASTER','ADMINISTRATOR')")
    public PaymentTransfers.Transfer check(UUID id) { return reconcile(id); }

    private PaymentTransfers.Transfer reconcile(UUID id) {
        var check = transfers.claimCheck(id);
        var t = check.transfer();
        if (check.lease() == null) return t;
        return transfers.checked(check, gateway.lookup(t.reference(), t.transactionId()));
    }

    @Scheduled(fixedDelayString = "${app.payments.araka.reconcile-delay:15000}")
    public void reconcileDue() {
        for (var id : transfers.due()) {
            try { reconcile(id); }
            catch (RuntimeException error) { log.warn("Payment reconciliation deferred: {}", error.getClass().getSimpleName()); }
        }
    }
}
