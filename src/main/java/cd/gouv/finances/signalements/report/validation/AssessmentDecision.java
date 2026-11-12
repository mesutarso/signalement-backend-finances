package cd.gouv.finances.signalements.report.validation;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AssessmentDecision {
    private final JdbcTemplate jdbc;
    private final AssessmentStore store;
    private final AssessmentEvidence evidence;
    private final FixedRewards rewards;
    private final Clock clock;

    public void apply(UUID reportId, String origin, UUID actorId) {
        var assessment = store.read(reportId);
        var uniqueness = "duplicate".equals(evidence.duplicateStatus(reportId)) ? "duplicate" : assessment.uniqueness();
        var decision = ValidationRules.assess(new ValidationRules.Facts(assessment.vatStatus(), assessment.normalization(),
                assessment.purchaseConformity(), assessment.vatCharged(), uniqueness, assessment.photoId() != null,
                !blank(assessment.sellerName()), evidence.unresolvedDuplicates(reportId)));
        if ("validated".equals(decision.state()) && rewards.alreadyRewarded(reportId)) {
            decision = new ValidationRules.Decision("doubt", null, "Une facture de même référence a déjà donné droit à une récompense. Vérifiez la date et le doublon.");
        }
        var now = Timestamp.from(clock.instant());
        var nextRevision = assessment.revision() + 1;
        jdbc.update("update app.report_assessment set revision = ?, reason = ?, updated_at = ? where report_id = ?",
                nextRevision, decision.reason(), now, reportId);
        jdbc.update("update app.report set validation_state = ? where id = ?", decision.state(), reportId);
        var snapshot = store.read(reportId);
        jdbc.update("""
                insert into app.assessment_event (id, report_id, revision, origin, actor_id, decision, reason, evidence, rule_version, recorded_at)
                values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """, UUID.randomUUID(), reportId, nextRevision, origin, actorId, decision.state(), decision.reason(), store.json(snapshot), ValidationRules.VERSION, now);
        recordQualification(assessment, decision, actorId, now);
        rewards.reconcile(reportId, decision.state(), actorId == null ? ValidationRules.AUTOMATION : actorId);
    }

    private void recordQualification(AssessmentStore.Snapshot assessment, ValidationRules.Decision decision, UUID actorId, Timestamp now) {
        var id = assessment.reportId();
        var actor = actorId == null ? ValidationRules.AUTOMATION : actorId;
        if ("doubt".equals(decision.state())) {
            // A previously granted finding must stop being eligible for payments and draws.
            var latest = jdbc.queryForList("select decision from app.qualification where report_id = ? order by recorded_at desc, id desc limit 1", String.class, id);
            if (latest.isEmpty() || !"confirm".equals(latest.getFirst())) return;
            jdbc.update("""
                    insert into app.qualification (id, report_id, decision, finding, proof, justification, missing_elements, actor_id, recorded_at)
                    values (?, ?, 'wait', 'verification', 'pending', ?, ?, ?, ?)
                    """, UUID.randomUUID(), id, decision.reason(), decision.reason(), actor, now);
        } else if ("validated".equals(decision.state())) {
            jdbc.update("""
                    insert into app.qualification (id, report_id, decision, finding, proof, justification,
                        seller_name, seller_tax_id, document_number, issued_on, amount, actor_id, recorded_at)
                    values (?, ?, 'confirm', ?, 'register', ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), id, decision.finding(), decision.reason(), assessment.sellerName(),
                    assessment.sellerTaxId(), assessment.documentNumber(), assessment.documentDate(),
                    bounded(assessment.extracted().get("totalAmount")), actor, now);
        } else {
            jdbc.update("""
                    insert into app.qualification (id, report_id, decision, justification, actor_id, recorded_at)
                    values (?, ?, 'reject', ?, ?, ?)
                    """, UUID.randomUUID(), id, decision.reason(), actor, now);
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String bounded(String value) { return blank(value) || value.length() > 40 ? null : value; }
}
