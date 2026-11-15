package cd.gouv.finances.signalements.report.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The saved decision and the two fiscal forecasts use exactly the same evidence and guards. */
@Component
@RequiredArgsConstructor
public class AssessmentEvaluation {
    private final AssessmentEvidence evidence;
    private final FixedRewards rewards;

    public record Result(ValidationRules.Decision decision, PrecalculatedAssessment precalculation) { }

    public Result calculate(AssessmentStore.Snapshot assessment) {
        var reportId = assessment.reportId();
        var uniqueness = "duplicate".equals(evidence.duplicateStatus(reportId)) ? "duplicate" : assessment.uniqueness();
        var facts = new ValidationRules.Facts(assessment.vatStatus(), assessment.normalization(),
                assessment.purchaseConformity(), assessment.vatCharged(), uniqueness, assessment.photoId() != null,
                assessment.sellerName() != null && !assessment.sellerName().isBlank(), evidence.unresolvedDuplicates(reportId));
        var alreadyRewarded = rewards.alreadyRewarded(reportId);
        var readability = evidence.readability(reportId, assessment.photoId());
        return new Result(ValidationRules.assess(facts, alreadyRewarded, readability),
                PrecalculatedAssessment.calculate(assessment, facts, alreadyRewarded, readability));
    }
}
