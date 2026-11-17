package cd.gouv.finances.signalements.report.validation;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.duplicate.AutomaticDuplicates;
import cd.gouv.finances.signalements.report.prime.BeneficiaryRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReviewAssessment {
    private final JdbcTemplate jdbc;
    private final AssessmentStore store;
    private final AssessmentEvidence evidence;
    private final AssessmentDecision decision;
    private final BeneficiaryRegistry beneficiaries;
    private final Clock clock;
    private final AutomaticDuplicates duplicates;

    @PreAuthorize("hasAuthority('report.review')")
    @Transactional
    public void fiscalStatus(UUID reportId, FiscalStatusReview review, UUID actorId) {
        beneficiaries.lock();
        var current = store.lock(reportId);
        requireCurrent(current, review.revision());
        requireNotScreened(current);
        require(review.vatStatus(), Set.of("registered", "unregistered"), fiscalEvidence(review.vatStatus(), review.vatEvidence()));
        var now = Timestamp.from(clock.instant());
        jdbc.update("""
                update app.report_assessment set vat_status = ?, vat_evidence = ?, vat_source = 'manual',
                    checked_by = ?, checked_at = ?, updated_at = ? where report_id = ?
                """, review.vatStatus(), fiscalEvidence(review.vatStatus(), review.vatEvidence()), actorId, now, now, reportId);
        decision.apply(reportId, "manual", actorId);
    }

    @PreAuthorize("hasAuthority('report.review')")
    @Transactional
    public void execute(UUID reportId, AssessmentReview review, UUID actorId) {
        beneficiaries.lock();
        var current = store.lock(reportId);
        requireCurrent(current, review.revision());
        requireNotScreened(current);
        var normalizationEvidence = "unknown".equals(review.normalization()) ? null
                : java.util.Objects.equals(review.normalization(), current.normalization()) && clean(current.normalizationEvidence()) != null
                ? current.normalizationEvidence() : "Normalisation renseignée par l’agent à partir de la facture.";
        require(review.normalization(), Set.of("unknown", "normalized", "non-normalized"), normalizationEvidence);
        LocalDate documentDate;
        try { documentDate = review.documentDate() == null ? null : LocalDate.parse(review.documentDate()); }
        catch (DateTimeParseException invalid) { throw new ReportRejected("qualification-date", 400); }
        var now = Timestamp.from(clock.instant());
        jdbc.update("""
                update app.report_assessment set seller_name = ?, seller_tax_id = ?, document_number = ?, document_date = ?,
                    normalization = ?, normalization_evidence = ?, updated_at = ? where report_id = ?
                """, clean(review.sellerName()), clean(review.sellerTaxId()), clean(review.documentNumber()),
                documentDate, review.normalization(), normalizationEvidence, now, reportId);
        if (!java.util.Objects.equals(clean(current.sellerTaxId()), clean(review.sellerTaxId()))) {
            jdbc.update("""
                    update app.report_assessment set vat_status = 'unknown', vat_evidence = null,
                        vat_source = null, checked_by = null, checked_at = null where report_id = ?
                    """, reportId);
        }
        duplicates.prepareUniqueness(reportId);
        decision.apply(reportId, "manual", actorId);
    }

    private static void require(String value, Set<String> allowed, String proof) {
        if (value == null || !allowed.contains(value)) throw new ReportRejected("assessment-value", 400);
        if (!"unknown".equals(value) && clean(proof) == null) throw new ReportRejected("assessment-evidence", 400);
    }

    private static void requireCurrent(AssessmentStore.Snapshot current, long revision) {
        if (current.revision() != revision) throw new ReportRejected("stale", 409);
        if (!"done".equals(current.jobState())) throw new ReportRejected("assessment-busy", 409);
    }

    private void requireNotScreened(AssessmentStore.Snapshot current) {
        if (evidence.taxIdMissingAfterReading(current.reportId(), current.photoId())
                || "duplicate".equals(evidence.duplicateStatus(current.reportId()))
                || "UNREADABLE".equals(evidence.readability(current.reportId(), current.photoId()))) {
            throw new ReportRejected("assessment-screened", 409);
        }
    }

    private static String clean(String text) { return text == null || text.isBlank() ? null : text.trim(); }

    private static String fiscalEvidence(String status, String proof) {
        if ("unknown".equals(status)) return null;
        return clean(proof) == null ? "Assujettissement renseigné temporairement par l’agent." : clean(proof);
    }
}
