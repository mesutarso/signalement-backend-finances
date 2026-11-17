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
        var missingTaxId = evidence.taxIdMissingAfterReading(reportId, assessment.photoId());
        var facts = new ValidationRules.Facts(assessment.vatStatus(), assessment.normalization(),
                assessment.purchaseConformity(), assessment.vatCharged(), uniqueness, assessment.photoId() != null,
                present(assessment.sellerTaxId()) && !missingTaxId, evidence.unresolvedDuplicates(reportId));
        var readability = evidence.readability(reportId, assessment.photoId());
        if (missingTaxId) {
            readability = "PARTIAL";
        }
        var screeningReason = ValidationRules.screeningReason(facts, readability);
        if (screeningReason != null) return new Result(ValidationRules.screen(facts, readability), null, screeningReason);
        var alreadyRewarded = rewards.alreadyRewarded(reportId);
        return new Result(ValidationRules.assess(facts, alreadyRewarded, readability),
                PrecalculatedAssessment.calculate(assessment, facts, alreadyRewarded, readability), null);
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }
}
