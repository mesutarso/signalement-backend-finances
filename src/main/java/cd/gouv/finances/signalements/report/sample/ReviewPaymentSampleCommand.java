package cd.gouv.finances.signalements.report.sample;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.PaymentSampleLine;
import cd.gouv.finances.signalements.report.repository.PaymentSampleLineRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReviewPaymentSampleCommand {

    private final PaymentSampleLineRepository paymentSampleLineRepository;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public void execute(UUID lineId, String result, String reason, UUID actorId) {
        var outcome = result == null ? "" : result.trim();
        var keptReason = reason == null ? "" : reason.trim();
        if (!PaymentSampleLine.CONFORM.equals(outcome) && !PaymentSampleLine.ANOMALY.equals(outcome)) {
            throw new ReportRejected("payment-sample-result", 400);
        }
        if (PaymentSampleLine.ANOMALY.equals(outcome) && (keptReason.isEmpty() || keptReason.length() > 500)) {
            throw new ReportRejected("payment-sample-reason", 400);
        }
        var line = paymentSampleLineRepository.findById(lineId).orElseThrow(() -> new ReportRejected("payment-sample-absent", 404));
        line.review(outcome, PaymentSampleLine.CONFORM.equals(outcome) ? null : keptReason, actorId, clock.instant());
    }
}
