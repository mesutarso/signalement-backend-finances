package cd.gouv.finances.signalements.report.declarant;

public final class DeclarantStatus {

    public static final String RECEIVED = "received";
    public static final String REVIEW = "review";
    public static final String REJECTED = "rejected";
    public static final String CONFIRMED = "confirmed";

    private DeclarantStatus() {
    }

    public static String of(String reportState, String validationState, String legacyDecision) {
        if ("validated".equals(validationState) || "confirm".equals(legacyDecision)) {
            return CONFIRMED;
        }
        if ("rejected".equals(validationState) || "reject".equals(legacyDecision)) {
            return REJECTED;
        }
        if ("analyzing".equals(validationState) || "doubt".equals(validationState)
                || "wait".equals(legacyDecision) || "assigned".equals(reportState)) {
            return REVIEW;
        }
        return RECEIVED;
    }
}
