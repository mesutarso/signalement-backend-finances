package cd.gouv.finances.signalements.report.payment;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.PaymentStatement;
import cd.gouv.finances.signalements.report.entity.PaymentStatementExclusion;
import cd.gouv.finances.signalements.report.entity.PaymentStatementLine;
import cd.gouv.finances.signalements.report.repository.PaymentStatementExclusionRepository;
import cd.gouv.finances.signalements.report.repository.PaymentStatementLineRepository;
import cd.gouv.finances.signalements.report.repository.PaymentStatementRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PreparePaymentStatementCommand {

    private static final ZoneId KINSHASA = ZoneId.of("Africa/Kinshasa");

    private final PaymentStatementRepository paymentStatementRepository;
    private final PaymentStatementLineRepository paymentStatementLineRepository;
    private final PaymentStatementExclusionRepository paymentStatementExclusionRepository;
    private final PaymentSourceLoader paymentSourceLoader;
    private final Clock clock;

    public record Prepared(UUID id, boolean created) {
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public Prepared execute(UUID actorId) {
        if (paymentStatementRepository.findFirstByStatusIn(List.of(PaymentStatement.DRAFT, PaymentStatement.CONTROLLED, PaymentStatement.VISAED)).isPresent()) {
            throw new ReportRejected("payment-open", 409);
        }
        var candidates = paymentSourceLoader.candidates();
        var eligible = candidates.stream().filter(PaymentSourceLoader::eligible).toList();
        if (eligible.isEmpty()) {
            throw new ReportRejected("payment-empty", 404);
        }
        var year = LocalDate.now(clock.withZone(KINSHASA)).getYear();
        var statement = new PaymentStatement(
                UUID.randomUUID(),
                "EP-" + year + "-" + "%06d".formatted(paymentStatementRepository.nextNumber()),
                actorId,
                clock.instant());
        paymentStatementRepository.save(statement);
        for (var candidate : eligible) {
            paymentStatementLineRepository.save(new PaymentStatementLine(
                    UUID.randomUUID(),
                    statement.getId(),
                    candidate.primeId(),
                    candidate.threshold(),
                    candidate.amount(),
                    candidate.payoutPhone()));
        }
        for (var candidate : candidates) {
            if (PaymentSourceLoader.eligible(candidate)) {
                continue;
            }
            paymentStatementExclusionRepository.save(new PaymentStatementExclusion(
                    UUID.randomUUID(),
                    statement.getId(),
                    candidate.primeId(),
                    PaymentSourceLoader.exclusionReason(candidate)));
        }
        return new Prepared(statement.getId(), true);
    }
}
