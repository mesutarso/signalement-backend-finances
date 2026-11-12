package cd.gouv.finances.signalements.report.validation;

import cd.gouv.finances.signalements.report.ReportRejected;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AnalysisJobs {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final cd.gouv.finances.signalements.report.prime.BeneficiaryRegistry beneficiaries;
    private final FixedRewards rewards;
    public record Job(UUID reportId, long generation, UUID leaseId) { }

    public void lock() { beneficiaries.lock(); }

    /** Also used by the importer, in its transaction, so jobs cannot outlive rolled-back photos. */
    @Transactional
    public void enqueue(UUID reportId, boolean photosChanged) {
        beneficiaries.lock();
        cd.gouv.finances.signalements.report.payment.PaymentHold.requireNoPending(jdbc, reportId);
        jdbc.update("""
                insert into app.report_assessment (report_id) select id from app.report
                where id = ? and validation_state <> 'legacy' on conflict do nothing
                """, reportId);
        var changed = jdbc.update("""
                update app.report_assessment set generation = generation + 1, revision = revision + 1,
                  job_state = case when job_state = 'running' and lease_until > current_timestamp then 'running' else 'queued' end,
                  attempts = 0, available_at = current_timestamp, error_code = null, updated_at = current_timestamp,
                  reason = 'Analyse des pièces en attente.',
                  photo_id = case when ? then null else photo_id end,
                  seller_name = case when ? then null else seller_name end,
                  seller_tax_id = case when ? then null else seller_tax_id end,
                  document_number = case when ? then null else document_number end,
                  document_date = case when ? then null else document_date end,
                  vat_status = case when ? then 'unknown' else vat_status end,
                  vat_evidence = case when ? then null else vat_evidence end,
                  vat_source = case when ? then null else vat_source end,
                  checked_by = case when ? then null else checked_by end,
                  checked_at = case when ? then null else checked_at end,
                  normalization = case when ? then 'unknown' else normalization end,
                  normalization_evidence = case when ? then null else normalization_evidence end,
                  purchase_conformity = case when ? then 'unknown' else purchase_conformity end,
                  purchase_evidence = case when ? then null else purchase_evidence end,
                  vat_charged = case when ? then 'unknown' else vat_charged end,
                  vat_charge_evidence = case when ? then null else vat_charge_evidence end,
                  unique_invoice = case when ? then 'unknown' else unique_invoice end,
                  uniqueness_evidence = case when ? then null else uniqueness_evidence end
                where report_id = ?
                """, photosChanged, photosChanged, photosChanged, photosChanged, photosChanged,
                photosChanged, photosChanged, photosChanged, photosChanged, photosChanged, photosChanged, photosChanged,
                photosChanged, photosChanged, photosChanged, photosChanged, photosChanged, photosChanged, reportId);
        if (changed > 0) {
            jdbc.update("update app.report set validation_state = 'queued' where id = ?", reportId);
            jdbc.update("""
                    insert into app.qualification (id, report_id, decision, finding, proof, justification, missing_elements, actor_id, recorded_at)
                    select ?, ?, 'wait', 'verification', 'pending', 'Nouvelle analyse des pièces.', 'Nouvelle analyse des pièces.', ?, current_timestamp
                    where (select decision from app.qualification where report_id = ? order by recorded_at desc, id desc limit 1) = 'confirm'
                    """, UUID.randomUUID(), reportId, ValidationRules.AUTOMATION, reportId);
            rewards.reconcile(reportId, "doubt", ValidationRules.AUTOMATION);
        }
    }

    @Transactional
    public Optional<Job> claim() {
        beneficiaries.lock();
        var rows = jdbc.query("""
                select report_id, generation from app.report_assessment
                where (job_state = 'queued' and available_at <= current_timestamp)
                   or (job_state = 'running' and lease_until < current_timestamp)
                order by available_at, report_id for update skip locked limit 1
                """, (r, n) -> new Job(r.getObject(1, UUID.class), r.getLong(2), UUID.randomUUID()));
        if (rows.isEmpty()) return Optional.empty();
        var job = rows.getFirst();
        jdbc.update("""
                update app.report_assessment set job_state = 'running', attempts = attempts + 1,
                    lease_until = current_timestamp + interval '10 minutes', lease_id = ? where report_id = ?
                """, job.leaseId(), job.reportId());
        jdbc.update("update app.report set validation_state = 'analyzing' where id = ?", job.reportId());
        return Optional.of(job);
    }

    public boolean renew(Job job) {
        return jdbc.update("""
                update app.report_assessment set lease_until = current_timestamp + interval '10 minutes'
                where report_id = ? and generation = ? and lease_id = ? and job_state = 'running'
                """, job.reportId(), job.generation(), job.leaseId()) == 1;
    }

    @Transactional
    public void failed(Job job, String code) {
        beneficiaries.lock();
        var rows = jdbc.query("select generation, attempts from app.report_assessment where report_id = ? and lease_id = ? for update",
                (r, n) -> new long[] {r.getLong(1), r.getLong(2)}, job.reportId(), job.leaseId());
        if (rows.isEmpty()) return;
        var current = rows.getFirst();
        var stale = current[0] != job.generation();
        var retry = stale || current[1] < 3;
        jdbc.update("""
                update app.report_assessment set job_state = ?, lease_until = null, error_code = ?,
                  available_at = ?, reason = ?, revision = revision + 1, updated_at = current_timestamp where report_id = ?
                """, retry ? "queued" : "done", code, Timestamp.from(clock.instant().plusSeconds(stale ? 0 : 60)),
                retry ? "L’analyse des pièces sera reprise." : "La lecture des pièces doit être vérifiée par un agent.", job.reportId());
        jdbc.update("update app.report set validation_state = ? where id = ?", retry ? "queued" : "doubt", job.reportId());
    }
}
