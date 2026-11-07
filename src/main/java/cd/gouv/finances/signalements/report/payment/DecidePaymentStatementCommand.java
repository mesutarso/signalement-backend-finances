package cd.gouv.finances.signalements.report.payment;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.PaymentStatement;
import cd.gouv.finances.signalements.report.repository.PaymentStatementLineRepository;
import cd.gouv.finances.signalements.report.repository.PaymentStatementRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DecidePaymentStatementCommand {

    public static final String ACCEPT = "accept";
    public static final String RETURN = "return";
    public static final String REFUSE = "refuse";
    public static final String CONTROL = "control";
    public static final String VISA = "visa";
    public static final String AUTHORIZE = "authorize";

    private static final ZoneId KINSHASA = ZoneId.of("Africa/Kinshasa");

    private final PaymentStatementRepository paymentStatementRepository;
    private final PaymentStatementLineRepository paymentStatementLineRepository;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public String control(UUID statementId, String result, String reason, UUID actorId) {
        return decide(statementId, CONTROL, result, reason, actorId);
    }

    @PreAuthorize("hasRole('CELLULE')")
    @Transactional
    public String visa(UUID statementId, String result, String reason, UUID actorId) {
        return decide(statementId, VISA, result, reason, actorId);
    }

    @PreAuthorize("hasRole('CABINET')")
    @Transactional
    public String authorize(UUID statementId, String result, String reason, UUID actorId) {
        return decide(statementId, AUTHORIZE, result, reason, actorId);
    }

    private String decide(UUID statementId, String step, String result, String reason, UUID actorId) {
        var decision = result == null ? "" : result.trim();
        var keptReason = reason == null ? "" : reason.trim();
        if (!ACCEPT.equals(decision) && !RETURN.equals(decision) && !REFUSE.equals(decision)) {
            throw new ReportRejected("payment-result", 400);
        }
        if (CONTROL.equals(step) && RETURN.equals(decision)) {
            throw new ReportRejected("payment-result", 400);
        }
        if (!ACCEPT.equals(decision) && (keptReason.isEmpty() || keptReason.length() > 500)) {
            throw new ReportRejected("payment-reason", 400);
        }
        var statement = paymentStatementRepository.findById(statementId).orElseThrow(() -> new ReportRejected("payment-absent", 404));
        var expected = CONTROL.equals(step) ? PaymentStatement.DRAFT : VISA.equals(step) ? PaymentStatement.CONTROLLED : PaymentStatement.VISAED;
        if (!expected.equals(statement.getStatus())) {
            throw new ReportRejected("payment-state", 409);
        }
        var now = clock.instant();
        if (ACCEPT.equals(decision)) {
            advance(statement, step, actorId, now);
            return action(step);
        }
        release(statementId);
        if (RETURN.equals(decision)) {
            statement.returnForCorrection(keptReason);
            return "payment-return";
        }
        statement.refuse(keptReason);
        return "payment-refusal";
    }

    private void advance(PaymentStatement statement, String step, UUID actorId, java.time.Instant now) {
        if (CONTROL.equals(step)) {
            statement.control(actorId, now);
            return;
        }
        if (VISA.equals(step)) {
            if (actorId.equals(statement.getControlledBy())) {
                throw new ReportRejected("payment-separation", 409);
            }
            statement.visa(actorId, now);
            return;
        }
        if (actorId.equals(statement.getVisaedBy()) || actorId.equals(statement.getControlledBy())) {
            throw new ReportRejected("payment-separation", 409);
        }
        var dueOn = PaymentDeadline.dueOn(LocalDate.ofInstant(now, KINSHASA));
        statement.authorize(actorId, now, dueOn);
    }

    private void release(UUID statementId) {
        for (var line : paymentStatementLineRepository.findByStatementId(statementId)) {
            line.release();
        }
    }

    private static String action(String step) {
        if (CONTROL.equals(step)) {
            return "payment-control";
        }
        if (VISA.equals(step)) {
            return "payment-visa";
        }
        return "payment-authorization";
    }
}
