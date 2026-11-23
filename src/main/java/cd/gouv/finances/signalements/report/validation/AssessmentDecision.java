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
    private final AssessmentEvaluation evaluation;
    private final FixedRewards rewards;
    private final Clock clock;

    public void apply(UUID reportId, String origin, UUID actorId) {
        var assessment = store.read(reportId);
        if ("rejected".equals(assessment.state()) && !previousAutomaticDuplicateWasCleared(reportId)) return;
        if ("validated".equals(assessment.state())) {
            var evaluated = evaluation.calculate(assessment);
            if ("duplicate".equals(evaluated.screeningReason())) {
                record(assessment, evaluated.decision(), "duplicate", origin, actorId);
            }
            return;
        }
        var now = Timestamp.from(clock.instant());
        var vatCharged = "yes".equals(AssessmentEvidence.charged(assessment.extracted().get("vatAmount")));
        if (vatCharged && !"ocr".equals(assessment.vatSource())) {
            jdbc.update("""
                    update app.report_assessment set vat_status = 'registered', vat_source = 'ocr',
                      vat_evidence = 'Montant de TVA positif relevé sur la facture par OCR.', checked_by = ?, checked_at = ?
                    where report_id = ?
                    """, ValidationRules.AUTOMATION, now, reportId);
        } else if (!vatCharged && "ocr".equals(assessment.vatSource())) {
            jdbc.update("""
                    update app.report_assessment set vat_status = 'unknown', vat_source = null,
                      vat_evidence = null, checked_by = null, checked_at = null where report_id = ?
                    """, reportId);
        }
        assessment = store.read(reportId);
        var evaluated = evaluation.calculate(assessment);
        var decision = evaluated.decision();
        if ("validated".equals(decision.state())) {
            jdbc.update("""
                    update app.report_assessment set normalization = 'non-normalized', normalization_evidence = ?
                    where report_id = ?
                    """, "Aucun élément de normalisation relevé sur la facture lisible par OCR.", reportId);
        }
        record(assessment, decision, evaluated.screeningReason(), origin, actorId);
    }

    private boolean previousAutomaticDuplicateWasCleared(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select r.screening_reason = 'duplicate'
                    and (select origin from app.assessment_event where report_id = r.id
                         order by revision desc limit 1) = 'automatic'
                    and not exists (
                        select 1 from app.duplicate_alert d where (d.report_id = r.id or d.other_report_id = r.id)
                        and d.status = 'confirmed' and d.priority_report_id <> r.id
                    )
                from app.report r where r.id = ?
                """, Boolean.class, id));
    }

    void record(AssessmentStore.Snapshot assessment, ValidationRules.Decision decision, String screeningReason,
                String origin, UUID actorId) {
        var reportId = assessment.reportId();
        var now = Timestamp.from(clock.instant());
        var nextRevision = assessment.revision() + 1;
        jdbc.update("update app.report_assessment set revision = ?, reason = ?, updated_at = ? where report_id = ?",
                nextRevision, decision.reason(), now, reportId);
        jdbc.update("update app.report set validation_state = ?, screening_reason = ? where id = ?",
                decision.state(), screeningReason, reportId);
        var snapshot = store.read(reportId);
        jdbc.update("""
                insert into app.assessment_event (id, report_id, revision, origin, actor_id, decision, reason, evidence, rule_version, recorded_at)
                values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """, UUID.randomUUID(), reportId, nextRevision, origin, actorId, decision.state(), decision.reason(), store.json(snapshot), ValidationRules.VERSION, now);
        recordQualification(assessment, decision, actorId, now);
        if ("validated".equals(assessment.state()) && "rejected".equals(decision.state())) {
            jdbc.update("""
                    update app.draw_ticket ticket set excluded_at = ?
                    where ticket.report_id = ? and ticket.excluded_at is null
                    and not exists (select 1 from app.draw_prize prize where prize.ticket_id = ticket.id)
                    """, now, reportId);
        }
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
            var proof = "ocr".equals(assessment.vatSource()) ? "photo" : "register";
            jdbc.update("""
                    insert into app.qualification (id, report_id, decision, finding, proof, justification,
                        seller_name, seller_tax_id, document_number, issued_on, amount, actor_id, recorded_at)
                    values (?, ?, 'confirm', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), id, decision.finding(), proof, decision.reason(), assessment.sellerName(),
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
