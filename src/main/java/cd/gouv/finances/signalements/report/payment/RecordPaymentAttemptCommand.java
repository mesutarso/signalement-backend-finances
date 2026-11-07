package cd.gouv.finances.signalements.report.payment;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.PaymentAttempt;
import cd.gouv.finances.signalements.report.entity.PaymentStatement;
import cd.gouv.finances.signalements.report.repository.PaymentAttemptRepository;
import cd.gouv.finances.signalements.report.repository.PaymentStatementLineRepository;
import cd.gouv.finances.signalements.report.repository.PaymentStatementRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RecordPaymentAttemptCommand {

    private final PaymentStatementRepository paymentStatementRepository;
    private final PaymentStatementLineRepository paymentStatementLineRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final Clock clock;

    @PreAuthorize("hasRole('PAYMASTER')")
    @Transactional
    public String execute(UUID statementId, UUID lineId, String operator, String result, String reason, UUID actorId) {
        var outcome = result == null ? "" : result.trim();
        if (!PaymentAttempt.PAID.equals(outcome) && !PaymentAttempt.FAILED.equals(outcome) && !PaymentAttempt.UNKNOWN.equals(outcome)) {
            throw new ReportRejected("payment-outcome", 400);
        }
        var keptReason = reason == null ? "" : reason.trim();
        if (keptReason.length() > 500) {
            throw new ReportRejected("payment-reason", 400);
        }
        if (!PaymentAttempt.PAID.equals(outcome) && keptReason.isEmpty()) {
            throw new ReportRejected("payment-reason", 400);
        }
        var statement = paymentStatementRepository.findById(statementId).orElseThrow(() -> new ReportRejected("payment-absent", 404));
        if (!PaymentStatement.AUTHORIZED.equals(statement.getStatus())) {
            throw new ReportRejected("payment-state", 409);
        }
        if (actorId.equals(statement.getAuthorizedBy())) {
            throw new ReportRejected("payment-separation", 409);
        }
        var channel = PaymentSimulation.channel(operator);
        var line = paymentStatementLineRepository.findById(lineId).orElseThrow(() -> new ReportRejected("payment-line", 404));
        if (!statementId.equals(line.getStatementId()) || line.isReleased()) {
            throw new ReportRejected("payment-line", 404);
        }
        var attempts = paymentAttemptRepository.findByLineIdOrderByRecordedAtAsc(lineId);
        if (attempts.stream().anyMatch(attempt -> PaymentAttempt.PAID.equals(attempt.getResult()))) {
            throw new ReportRejected("payment-paid", 409);
        }
        if (!attempts.isEmpty() && PaymentAttempt.UNKNOWN.equals(attempts.getLast().getResult()) && PaymentAttempt.UNKNOWN.equals(outcome)) {
            throw new ReportRejected("payment-uncertain", 409);
        }
        paymentAttemptRepository.save(new PaymentAttempt(
                UUID.randomUUID(),
                statementId,
                lineId,
                channel,
                outcome,
                PaymentSimulation.reference(channel, paymentAttemptRepository.nextNumber()),
                PaymentAttempt.PAID.equals(outcome) ? null : keptReason,
                actorId,
                clock.instant()));
        return "payment-" + outcome;
    }
}
