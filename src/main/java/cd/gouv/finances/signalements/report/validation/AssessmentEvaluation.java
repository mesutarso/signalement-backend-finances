package cd.gouv.finances.signalements.report.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The saved decision and the two fiscal forecasts use exactly the same evidence and guards. */
@Component
@RequiredArgsConstructor
public class AssessmentEvaluation {
    private final AssessmentEvidence evidence;
    private final FixedRewards rewards;

    public record Result(ValidationRules.Decision decision, PrecalculatedAssessment precalculation, String screeningReason) { }

    public Result calculate(AssessmentStore.Snapshot assessment) {
        var reportId = assessment.reportId();
        var uniqueness = "duplicate".equals(evidence.duplicateStatus(reportId)) ? "duplicate" : assessment.uniqueness();
        var facts = new ValidationRules.Facts(assessment.vatStatus(),
                assessment.vatCharged(), uniqueness, assessment.photoId() != null,
                present(assessment.sellerTaxId()), evidence.unresolvedDuplicates(reportId));
        var readability = evidence.readability(reportId, assessment.photoId());
        var markers = AssessmentEvidence.hasCompleteNormalization(assessment);
        var screeningReason = ValidationRules.screeningReason(facts, readability, markers);
        var alreadyRewarded = rewards.alreadyRewarded(reportId);
        var decision = ValidationRules.assess(facts, alreadyRewarded, readability, markers);
        return new Result(decision,
                PrecalculatedAssessment.calculate(assessment, facts, alreadyRewarded, readability, markers), screeningReason);
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }
}
