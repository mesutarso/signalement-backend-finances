package cd.gouv.finances.signalements.report.validation;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.prime.BeneficiaryRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
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

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public void execute(UUID reportId, AssessmentReview review, UUID actorId) {
        beneficiaries.lock();
        var current = store.lock(reportId);
        if (current.revision() != review.revision()) throw new ReportRejected("stale", 409);
        if (!"done".equals(current.jobState())) throw new ReportRejected("assessment-busy", 409);
        require(review.vatStatus(), Set.of("unknown", "registered", "unregistered"), review.vatEvidence());
        require(review.normalization(), Set.of("unknown", "normalized", "non-normalized"), review.normalizationEvidence());
        require(review.purchaseConformity(), Set.of("unknown", "conform", "nonconform"), review.purchaseEvidence());
        require(review.vatCharged(), Set.of("unknown", "yes", "no"), review.vatChargeEvidence());
        require(review.uniqueness(), Set.of("unknown", "unique", "duplicate"), review.uniquenessEvidence());
        var photoId = evidence.selected(reportId, review.photoId());
        LocalDate documentDate;
        try { documentDate = review.documentDate() == null ? null : LocalDate.parse(review.documentDate()); }
        catch (DateTimeParseException invalid) { throw new ReportRejected("qualification-date", 400); }
        var reading = photoId == null ? null : evidence.latest(photoId);
        var fields = reading == null ? Map.<String, String>of() : reading.fields();
        var now = Timestamp.from(clock.instant());
        jdbc.update("""
                update app.report_assessment set photo_id = ?, seller_name = ?, seller_tax_id = ?, document_number = ?, document_date = ?,
                    vat_status = ?, vat_evidence = ?, vat_source = ?, checked_by = ?, checked_at = ?,
                    normalization = ?, normalization_evidence = ?, purchase_conformity = ?, purchase_evidence = ?,
                    vat_charged = ?, vat_charge_evidence = ?, unique_invoice = ?, uniqueness_evidence = ?,
                    extracted = ?::jsonb, updated_at = ? where report_id = ?
                """, photoId, clean(review.sellerName()), clean(review.sellerTaxId()), clean(review.documentNumber()),
                documentDate,
                review.vatStatus(), clean(review.vatEvidence()), "unknown".equals(review.vatStatus()) ? null : "manual",
                "unknown".equals(review.vatStatus()) ? null : actorId, "unknown".equals(review.vatStatus()) ? null : now,
                review.normalization(), clean(review.normalizationEvidence()), review.purchaseConformity(), clean(review.purchaseEvidence()),
                review.vatCharged(), clean(review.vatChargeEvidence()), review.uniqueness(), clean(review.uniquenessEvidence()),
                store.json(fields), now, reportId);
        decision.apply(reportId, "manual", actorId);
    }

    private static void require(String value, Set<String> allowed, String proof) {
        if (value == null || !allowed.contains(value)) throw new ReportRejected("assessment-value", 400);
        if (!"unknown".equals(value) && clean(proof) == null) throw new ReportRejected("assessment-evidence", 400);
    }

    private static String clean(String text) { return text == null || text.isBlank() ? null : text.trim(); }
}
