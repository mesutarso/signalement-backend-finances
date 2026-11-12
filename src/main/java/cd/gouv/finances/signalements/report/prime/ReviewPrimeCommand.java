package cd.gouv.finances.signalements.report.prime;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.Prime;
import cd.gouv.finances.signalements.report.entity.PrimeReview;
import cd.gouv.finances.signalements.report.repository.PayoutNumberRepository;
import cd.gouv.finances.signalements.report.repository.PrimeRepository;
import cd.gouv.finances.signalements.report.repository.PrimeReviewRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReviewPrimeCommand {

    private final PrimeRepository primeRepository;
    private final PrimeReviewRepository primeReviewRepository;
    private final PayoutNumberRepository payoutNumberRepository;
    private final RecordPayoutCommand recordPayoutCommand;
    private final PrimeGuard primeGuard;
    private final BeneficiaryRegistry beneficiaries;
    private final Clock clock;
    private final cd.gouv.finances.signalements.report.validation.RewardPaymentGuard rewardPaymentGuard;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public record Outcome(String action, UUID payoutId) {
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public Outcome execute(UUID primeId, String result, String reason, UUID actorId) {
        var decision = result == null ? "" : result.trim();
        var keptReason = reason == null ? "" : reason.trim();
        if (!PrimeReview.APPROVED.equals(decision) && !PrimeReview.RETURNED.equals(decision)) {
            throw new ReportRejected("prime-result", 400);
        }
        if (PrimeReview.RETURNED.equals(decision) && (keptReason.isEmpty() || keptReason.length() > 500)) {
            throw new ReportRejected("prime-reason", 400);
        }
        if (PrimeReview.APPROVED.equals(decision)) {
            keptReason = "";
        }
        beneficiaries.lock();
        var prime = primeRepository.findById(primeId).orElseThrow(() -> new ReportRejected("prime-absent", 404));
        if (prime.getReportId() == null) throw new ReportRejected("prime-absent", 404);
        if (!Prime.PROPOSED.equals(prime.getStatus())) {
            throw new ReportRejected("prime-state", 409);
        }
        var now = clock.instant();
        var payoutId = recordPayoutCommand.execute(prime.getDeclarantId(), actorId).orElse(null);
        if (PrimeReview.APPROVED.equals(decision)) {
            if (primeGuard.suspended(prime.getDeclarantId())) {
                throw new ReportRejected("prime-suspended", 409);
            }
            var payout = payoutNumberRepository.findByDeclarantId(prime.getDeclarantId()).orElseThrow(() -> new ReportRejected("prime-phone", 409));
            if (!PrimeContext.READY.equals(payout.getState()) || payout.getPhone() == null || !payout.getPhone().equals(prime.getPhone())) {
                throw new ReportRejected("prime-phone", 409);
            }
            rewardPaymentGuard.requireValidated(prime.getReportId());
            var actors = jdbc.queryForList("select distinct actor_id from app.assessment_event where report_id = ? and origin = 'manual'", UUID.class, prime.getReportId());
            if (actors.contains(actorId)) {
                throw new ReportRejected("prime-separation", 409);
            }
            beneficiaries.requireCapacity(prime);
            prime.approve(actorId, now);
        } else {
            prime.returnForCorrection(actorId, now);
        }
        primeReviewRepository.save(new PrimeReview(
                UUID.randomUUID(),
                prime.getId(),
                decision,
                keptReason.isEmpty() ? null : keptReason,
                actorId,
                now));
        var action = PrimeReview.APPROVED.equals(decision) ? "reward-approval" : "reward-return";
        return new Outcome(action, payoutId);
    }
}
