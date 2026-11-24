package cd.gouv.finances.signalements.report.validation;

import cd.gouv.finances.signalements.report.duplicate.AutomaticDuplicates;
import cd.gouv.finances.signalements.report.payment.PaymentHold;
import cd.gouv.finances.signalements.report.prime.BeneficiaryRegistry;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ExceptionalAssessment {
    static final String FINDING = "exceptional-validation";

    private final JdbcTemplate jdbc;
    private final AssessmentStore store;
    private final AssessmentDecision decisions;
    private final AssessmentEvidence evidence;
    private final AutomaticDuplicates duplicates;
    private final FixedRewards rewards;
    private final BeneficiaryRegistry beneficiaries;

    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Transactional
    public boolean validate(UUID report, String reason, UUID actor) {
        beneficiaries.lock();
        jdbc.update("""
                insert into app.report_assessment (report_id, job_state)
                select id, 'done' from app.report where id = ? on conflict do nothing
                """, report);
        var current = store.lock(report);
        if ("validated".equals(current.state()) || "duplicate".equals(evidence.duplicateStatus(report))
                || evidence.unresolvedDuplicates(report) || "duplicate".equals(current.uniqueness())) return false;
        PaymentHold.requireNoPending(jdbc, report);
        if (current.photoId() == null) {
            jdbc.update("update app.report_assessment set photo_id = ? where report_id = ?",
                    evidence.selected(report, null), report);
        }
        if (!"done".equals(current.jobState())) duplicates.execute(report);
        if ("duplicate".equals(evidence.duplicateStatus(report)) || evidence.unresolvedDuplicates(report)
                || rewards.alreadyRewarded(report)) return false;

        jdbc.update("""
                update app.report_assessment set job_state = 'done', lease_id = null, lease_until = null
                where report_id = ?
                """, report);
        decisions.record(store.read(report), new ValidationRules.Decision("validated", FINDING, reason), null, "manual", actor);
        return true;
    }
}
