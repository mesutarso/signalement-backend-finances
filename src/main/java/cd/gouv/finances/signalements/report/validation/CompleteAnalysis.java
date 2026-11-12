package cd.gouv.finances.signalements.report.validation;

import cd.gouv.finances.signalements.report.prime.BeneficiaryRegistry;
import java.util.Map;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CompleteAnalysis {
    private final JdbcTemplate jdbc;
    private final AssessmentStore store;
    private final AssessmentEvidence evidence;
    private final AssessmentDecision decision;
    private final BeneficiaryRegistry beneficiaries;

    @Transactional
    public void execute(AnalysisJobs.Job job) {
        beneficiaries.lock();
        var assessment = store.lock(job.reportId());
        var owner = jdbc.queryForObject("select lease_id from app.report_assessment where report_id = ?", java.util.UUID.class, job.reportId());
        if (!job.leaseId().equals(owner)) return;
        var generation = jdbc.queryForObject("select generation from app.report_assessment where report_id = ?", Long.class, job.reportId());
        if (generation == null || generation != job.generation()) {
            jdbc.update("update app.report_assessment set job_state = 'queued', lease_until = null where report_id = ?", job.reportId());
            jdbc.update("update app.report set validation_state = 'queued' where id = ?", job.reportId());
            return;
        }
        var photoId = evidence.selected(job.reportId(), assessment.photoId());
        var reading = photoId == null ? null : evidence.latest(photoId);
        var fields = reading == null || !"READ".equals(reading.status()) ? Map.<String, String>of() : reading.fields();
        var charged = AssessmentEvidence.charged(fields.get("vatAmount"));
        jdbc.update("""
                update app.report_assessment set job_state = 'done', lease_until = null, error_code = ?, photo_id = ?,
                  extracted = ?::jsonb, reading_ids = ?::jsonb,
                  seller_name = coalesce(seller_name, ?), seller_tax_id = coalesce(seller_tax_id, ?),
                  document_number = coalesce(document_number, ?), document_date = coalesce(document_date, ?),
                  vat_charged = case when vat_charge_evidence is null then ? else vat_charged end,
                  vat_charge_evidence = case when vat_charge_evidence is null and ? <> 'unknown'
                    then 'TVA relevée sur la pièce jointe ; à vérifier sur l’image.' else vat_charge_evidence end
                where report_id = ?
                """, reading == null ? null : reading.errorCode(), photoId, store.json(fields), store.json(evidence.readings(job.reportId())),
                fields.get("sellerName"), fields.get("sellerTaxId"), fields.get("documentNumber"), date(fields.get("documentDate")), charged, charged, job.reportId());
        // Reading an invoice does not establish its fiscal status, authenticity, or the actual purchase.
        decision.apply(job.reportId(), "automatic", null);
    }

    private static LocalDate date(String value) {
        try { return value == null ? null : LocalDate.parse(value); }
        catch (DateTimeParseException invalid) { return null; }
    }
}
