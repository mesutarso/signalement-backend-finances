package cd.gouv.finances.signalements.report.qualification;

import cd.gouv.finances.signalements.report.ReportRejected;
import java.util.Set;

public final class QualificationRules {

    public static final String PHOTO = "photo";
    public static final String REGISTER = "register";
    public static final String PENDING = "pending";
    public static final String AUTHORITY = "authority";
    public static final String NON_NORMALIZED = "non-normalized";
    public static final String REFUSAL = "refusal";

    private static final Set<String> REGISTER_FINDINGS = Set.of(
            "unknown-seller",
            "fake-invoice",
            "understated",
            "missing-transmission");

    private QualificationRules() {
    }

    public record Accepted(String decision, String finding, String proof, String missing, String reference) {
    }

    public static Accepted accept(
            String decision,
            String finding,
            String proof,
            boolean corrected,
            String missing,
            String reference,
            boolean waiting) {
        if ("wait".equals(decision)) {
            if (!REFUSAL.equals(finding) || !PENDING.equals(proof) || reference != null) {
                throw new ReportRejected("qualification-finding", 400);
            }
            if (missing == null) {
                throw new ReportRejected("qualification-missing", 400);
            }
            return new Accepted("wait", REFUSAL, PENDING, missing, null);
        }
        if ("reject".equals(decision)) {
            if (finding != null || proof != null || corrected || missing != null) {
                throw new ReportRejected(finding != null || proof != null ? "qualification-finding" : "qualification-correction", 400);
            }
            if (waiting && reference == null) {
                throw new ReportRejected("qualification-reference", 400);
            }
            if (!waiting && reference != null) {
                throw new ReportRejected("qualification-reference", 400);
            }
            return new Accepted("reject", null, null, null, reference);
        }
        if (!"confirm".equals(decision)) {
            throw new ReportRejected("qualification-decision", 400);
        }
        if (REFUSAL.equals(finding)) {
            if (!waiting) {
                throw new ReportRejected("qualification-waiting", 400);
            }
            if (!AUTHORITY.equals(proof) || reference == null || missing != null) {
                throw new ReportRejected(reference == null ? "qualification-reference" : "qualification-finding", 400);
            }
            return new Accepted("confirm", REFUSAL, AUTHORITY, null, reference);
        }
        if (missing != null || reference != null) {
            throw new ReportRejected("qualification-correction", 400);
        }
        if (NON_NORMALIZED.equals(finding) && (PHOTO.equals(proof) || REGISTER.equals(proof))) {
            return new Accepted("confirm", finding, proof, null, null);
        }
        if (REGISTER_FINDINGS.contains(finding) && REGISTER.equals(proof)) {
            return new Accepted("confirm", finding, proof, null, null);
        }
        if (REGISTER_FINDINGS.contains(finding) && PHOTO.equals(proof)) {
            throw new ReportRejected("qualification-proof", 400);
        }
        throw new ReportRejected("qualification-finding", 400);
    }
}
