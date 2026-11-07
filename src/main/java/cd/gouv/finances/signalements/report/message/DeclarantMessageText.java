package cd.gouv.finances.signalements.report.message;

import cd.gouv.finances.signalements.report.ReportRejected;
import java.util.Map;

public final class DeclarantMessageText {

    public static final String PHOTO = "photo";
    public static final String PAYOUT = "payout";
    public static final String REEXAMINATION = "reexamination";
    public static final String SITUATION = "situation";

    private static final Map<String, String> PATTERNS = Map.of(
            PHOTO, "Le signalement %s attend une nouvelle photo.",
            PAYOUT, "Le signalement %s attend un numéro de paiement.",
            REEXAMINATION, "La demande de réexamen du signalement %s a été traitée.",
            SITUATION, "La situation du signalement %s a été mise à jour.");

    private DeclarantMessageText() {
    }

    public static String body(String kind, String reference) {
        var pattern = PATTERNS.get(kind);
        if (pattern == null || reference == null || reference.isBlank()) {
            throw new ReportRejected("message-kind", 400);
        }
        return pattern.formatted(reference.trim());
    }
}
