package cd.gouv.finances.signalements.report.declarant;

public final class DeclarantStatus {

    public static final String RECEIVED = "received";
    public static final String REVIEW = "review";
    public static final String REJECTED = "rejected";
    public static final String CONFIRMED = "confirmed";
    public static final String POINTS = "points";

    private DeclarantStatus() {
    }

    public static String of(String reportState, String decision, int netPoints) {
        if (netPoints > 0) {
            return POINTS;
        }
        if ("confirm".equals(decision)) {
            return CONFIRMED;
        }
        if ("reject".equals(decision)) {
            return REJECTED;
        }
        if ("wait".equals(decision) || "assigned".equals(reportState)) {
            return REVIEW;
        }
        return RECEIVED;
    }
}
