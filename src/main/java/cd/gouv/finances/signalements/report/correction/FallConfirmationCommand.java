package cd.gouv.finances.signalements.report.correction;

import cd.gouv.finances.signalements.report.ReportReference;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.DecisionCorrection;
import cd.gouv.finances.signalements.report.repository.DecisionCorrectionRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FallConfirmationCommand {

    private final JdbcTemplate jdbc;
    private final ReportReference reportReference;
    private final DecisionCorrectionRepository decisionCorrectionRepository;
    private final Clock clock;
    private final cd.gouv.finances.signalements.report.prime.BeneficiaryRegistry beneficiaries;
    private final cd.gouv.finances.signalements.report.validation.AssessmentStore assessmentStore;
    private final cd.gouv.finances.signalements.report.validation.FixedRewards fixedRewards;

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public UUID execute(String reference, String reason, UUID actorId) {
        var keptReason = reason == null ? "" : reason.trim();
        if (keptReason.isEmpty() || keptReason.length() > 500) {
            throw new ReportRejected("correction-reason", 400);
        }
        var report = reportReference.require(reference);
        beneficiaries.lock();
        var rows = jdbc.query("""
                select id, decision
                from app.qualification
                where report_id = ?
                order by recorded_at desc, id desc
                limit 1
                """, (row, number) -> new Latest(row.getObject(1, UUID.class), row.getString(2)), report.id());
        if (rows.isEmpty() || !"confirm".equals(rows.getFirst().decision())) {
            throw new ReportRejected("correction-state", 409);
        }
        var qualificationId = rows.getFirst().id();
        if (decisionCorrectionRepository.existsByQualificationId(qualificationId)) {
            throw new ReportRejected("correction-open", 409);
        }
        var now = clock.instant();
        var correction = new DecisionCorrection(UUID.randomUUID(), report.id(), qualificationId, keptReason, actorId, now);
        decisionCorrectionRepository.save(correction);
        jdbc.update("""
                update app.draw_ticket ticket
                set excluded_at = ?
                where ticket.report_id = ?
                  and ticket.excluded_at is null
                  and not exists (select 1 from app.draw_prize prize where prize.ticket_id = ticket.id)
                """, Timestamp.from(now), report.id());
        // Close the old finding as well: a replay must not credit or ticket it again.
        jdbc.update("""
                insert into app.qualification (id, report_id, decision, justification, actor_id, recorded_at)
                values (?, ?, 'reject', ?, ?, ?)
                """, UUID.randomUUID(), report.id(), keptReason, actorId, Timestamp.from(now));
        var modern = jdbc.queryForObject("select exists(select 1 from app.report_assessment where report_id = ?)", Boolean.class, report.id());
        if (Boolean.TRUE.equals(modern)) {
            var previous = assessmentStore.lock(report.id());
            jdbc.update("update app.report set validation_state = 'rejected' where id = ?", report.id());
            jdbc.update("""
                    update app.report_assessment set revision = revision + 1, generation = generation + 1,
                        job_state = 'done', lease_id = null, lease_until = null, reason = ?, updated_at = ? where report_id = ?
                    """, keptReason, Timestamp.from(now), report.id());
            jdbc.update("""
                    insert into app.assessment_event values (?, ?, ?, 'correction', ?, 'rejected', ?, ?::jsonb, ?, ?)
                    """, UUID.randomUUID(), report.id(), previous.revision() + 1, actorId, keptReason,
                    assessmentStore.json(assessmentStore.read(report.id())),
                    cd.gouv.finances.signalements.report.validation.ValidationRules.VERSION, Timestamp.from(now));
            fixedRewards.reconcile(report.id(), "rejected", actorId);
        }
        return correction.getId();
    }

    private record Latest(UUID id, String decision) {
    }
}
