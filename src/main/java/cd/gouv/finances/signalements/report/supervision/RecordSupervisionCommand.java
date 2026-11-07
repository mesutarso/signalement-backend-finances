package cd.gouv.finances.signalements.report.supervision;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.SupervisionReview;
import cd.gouv.finances.signalements.report.repository.QualificationRepository;
import cd.gouv.finances.signalements.report.repository.SupervisionReviewRepository;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RecordSupervisionCommand {

    private static final Set<String> RESULTS = Set.of(SupervisionReview.UPHELD, SupervisionReview.RETURNED);

    private final QualificationRepository qualificationRepository;
    private final SupervisionReviewRepository supervisionReviewRepository;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public boolean execute(UUID reportId, String result, String reason, UUID actorId) {
        if (!RESULTS.contains(result)) {
            throw new ReportRejected("supervision-result", 400);
        }
        var qualifications = qualificationRepository.findByReportIdOrderByRecordedAtDesc(reportId);
        if (qualifications.isEmpty()) {
            throw new ReportRejected("supervision-absent", 404);
        }
        var qualification = qualifications.getFirst();
        var trimmed = reason.trim();
        var existing = supervisionReviewRepository.findFirstByQualificationIdOrderByRecordedAtDesc(qualification.getId());
        if (existing.isPresent()
                && existing.get().getResult().equals(result)
                && existing.get().getReason().equals(trimmed)) {
            return false;
        }
        supervisionReviewRepository.save(new SupervisionReview(
                UUID.randomUUID(),
                reportId,
                qualification.getId(),
                result,
                trimmed,
                actorId,
                clock.instant()));
        return true;
    }
}
