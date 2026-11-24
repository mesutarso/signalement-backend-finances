package cd.gouv.finances.signalements.report.validation;

import cd.gouv.finances.signalements.access.Role;
import cd.gouv.finances.signalements.access.security.AccountUserDetails;
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
    private final FixedRewards rewards;

    @PreAuthorize("hasAuthority('report.review')")
    @Transactional
    public void fiscalStatus(UUID reportId, FiscalStatusReview review, UUID actorId) {
        beneficiaries.lock();
        var current = store.lock(reportId);
        requireCurrent(current, review.revision());
        requirePending(current);
        requireNotScreened(current);
        if ("yes".equals(current.vatCharged())) throw new ReportRejected("assessment-vat-established", 409);
        if (AssessmentEvidence.hasNormalizationMarkers(current)) {
            throw new ReportRejected("assessment-normalization-review", 409);
        }
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
        requirePending(current);
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

    @PreAuthorize("hasAuthority('report.review')")
    @Transactional
    public void decide(UUID reportId, AssessmentResolution body, AccountUserDetails account) {
        beneficiaries.lock();
        var current = store.lock(reportId);
        requireCurrent(current, body.revision());
        var administrator = account.getRole() == Role.ADMINISTRATOR;
        if (!administrator || !Set.of("validated", "rejected").contains(current.state())) {
            requirePending(current);
        } else {
            requireUnpaid(reportId);
        }

        var confirmedDuplicate = "duplicate".equals(evidence.duplicateStatus(reportId));
        var resolved = switch (body.decision()) {
            case "validate" -> validation(current, body.code(), confirmedDuplicate);
            case "reject" -> rejection(current, body.code(), confirmedDuplicate);
            default -> throw new ReportRejected("assessment-value", 400);
        };
        var screeningReason = "reject".equals(body.decision()) && Set.of("duplicate", "unreadable").contains(body.code())
                ? body.code() : null;
        decision.record(current, resolved, screeningReason, "manual", account.getAccountId());
    }

    private ValidationRules.Decision validation(AssessmentStore.Snapshot current, String category, boolean confirmedDuplicate) {
        var label = ValidationRules.FINDINGS.get(category);
        if (label == null) throw new ReportRejected("assessment-value", 400);
        var reportId = current.reportId();
        if (confirmedDuplicate || evidence.unresolvedDuplicates(reportId)) {
            throw new ReportRejected("assessment-duplicate-review", 409);
        }
        if (requiresFiscalReview(current) && !"registered".equals(current.vatStatus())) {
            throw new ReportRejected("assessment-vat-required", 409);
        }
        if (current.photoId() == null) {
            var selectedPhoto = evidence.selected(reportId, null);
            if (selectedPhoto == null) throw new ReportRejected("assessment-document", 409);
            jdbc.update("update app.report_assessment set photo_id = ? where report_id = ?", selectedPhoto, reportId);
        }
        if (rewards.alreadyRewarded(reportId)) throw new ReportRejected("assessment-duplicate-review", 409);
        recordNormalization(current.reportId(), "non-normalized", label);
        return new ValidationRules.Decision("validated", category, label + ".");
    }

    private ValidationRules.Decision rejection(AssessmentStore.Snapshot current, String code, boolean confirmedDuplicate) {
        var label = ValidationRules.REJECTIONS.get(code);
        if (label == null) throw new ReportRejected("assessment-value", 400);
        if (confirmedDuplicate && !"duplicate".equals(code)) throw new ReportRejected("assessment-screened", 409);
        var fiscalReviewRequired = requiresFiscalReview(current);
        if ("unregistered".equals(code) && (!fiscalReviewRequired || !"unregistered".equals(current.vatStatus()))) {
            throw new ReportRejected("assessment-vat-required", 409);
        }
        if ("normalized".equals(code)) {
            if (fiscalReviewRequired && "unknown".equals(current.vatStatus())) {
                throw new ReportRejected("assessment-vat-required", 409);
            }
            recordNormalization(current.reportId(), "normalized", label);
        }
        return new ValidationRules.Decision("rejected", null, label);
    }

    private void recordNormalization(UUID reportId, String normalization, String evidenceLabel) {
        jdbc.update("""
                update app.report_assessment set normalization = ?, normalization_evidence = ? where report_id = ?
                """, normalization, evidenceLabel, reportId);
    }

    private static boolean requiresFiscalReview(AssessmentStore.Snapshot assessment) {
        return ValidationRules.requiresFiscalReview(assessment.vatCharged(), AssessmentEvidence.hasNormalizationMarkers(assessment));
    }

    private void requireUnpaid(UUID reportId) {
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from app.prime p join app.payment_statement_line l on l.prime_id = p.id
                  join app.payment_attempt a on a.line_id = l.id where p.report_id = ?
                  and a.result = 'paid' and a.reference_code not like 'SIM-%')
                """, Boolean.class, reportId))) throw new ReportRejected("assessment-paid", 409);
    }

    private static void requirePending(AssessmentStore.Snapshot current) {
        if (!"doubt".equals(current.state())) throw new ReportRejected("assessment-decided", 409);
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
        if ("duplicate".equals(evidence.duplicateStatus(current.reportId()))) {
            throw new ReportRejected("assessment-screened", 409);
        }
    }

    private static String clean(String text) { return text == null || text.isBlank() ? null : text.trim(); }

    private static String fiscalEvidence(String status, String proof) {
        if ("unknown".equals(status)) return null;
        return clean(proof);
    }
}
