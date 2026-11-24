package cd.gouv.finances.signalements.report.message;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.sms.SmsText;
import java.util.Map;
import java.util.regex.Pattern;

public final class DeclarantMessageText {

    public static final int SMS_LIMIT = SmsText.LIMIT;
    public static final int REFERENCE_RESERVE = 36;

    public static final String PHOTO = "photo";
    public static final String PAYOUT = "payout";
    public static final String REEXAMINATION = "reexamination";
    public static final String SITUATION = "situation";

    private static final Map<String, String> PATTERNS = Map.of(
            PHOTO, "Facture %s : renvoyez une photo nette et entière en rappelant cette référence.",
            PAYOUT, "Ajoutez votre numéro Mobile Money dans votre espace pour recevoir votre prime.",
            REEXAMINATION, "Votre réexamen est terminé. Consultez la réponse en ligne.",
            SITUATION, "Votre signalement a été mis à jour. Consultez votre espace.");

    private static final Map<String, String> LEGACY = Map.of(
            PAYOUT, "Le signalement %s attend un numéro de paiement.",
            REEXAMINATION, "La demande de réexamen du signalement %s a été traitée.",
            SITUATION, "La situation du signalement %s a été mise à jour.");

    private DeclarantMessageText() {
    }

    public static String pattern(String kind) {
        var pattern = PATTERNS.get(kind);
        if (pattern == null) {
            throw new ReportRejected("message-kind", 400);
        }
        return pattern;
    }

    public static String body(String kind, String reference) {
        return render(pattern(kind), reference);
    }

    public static String effectivePattern(String kind, String stored, boolean systemDefault) {
        var fallback = pattern(kind);
        if (stored == null || systemDefault && stored.equals(LEGACY.get(kind))) return fallback;
        if (PHOTO.equals(kind)) return stored;
        var text = withoutReportReference(stored.replace("%s", " "), null);
        return text.isBlank() ? fallback : text;
    }

    static String validateReportMessage(String body) {
        var text = body == null ? "" : body.trim();
        if (text.isEmpty() || text.contains("%s") || controls(text)) throw new ReportRejected("model-body", 400);
        if (text.length() > SMS_LIMIT) throw new ReportRejected("model-length", 400);
        return text;
    }

    static String withoutReportReference(String body, String reference) {
        var text = body;
        if (reference != null && !reference.isBlank()) {
            var token = "(?<![\\p{L}\\p{N}])" + Pattern.quote(reference) + "(?![\\p{L}\\p{N}])";
            text = text.replaceAll(token, " ");
        }
        return text.replaceAll("\\(\\s*\\)|\\[\\s*]", "")
                .replaceAll(" {2,}", " ").replaceAll(" +([.,])", "$1").strip();
    }

    public static String validatePattern(String body, int referenceLength) {
        var kept = body == null ? "" : body.trim();
        if (kept.isEmpty() || kept.split("%s", -1).length != 2 || controls(kept)) {
            throw new ReportRejected("model-body", 400);
        }
        if (kept.length() - 2 + Math.max(REFERENCE_RESERVE, referenceLength) > SMS_LIMIT) {
            throw new ReportRejected("model-length", 400);
        }
        return kept;
    }

    public static String render(String pattern, String reference) {
        if (reference == null || reference.isBlank()) {
            throw new ReportRejected("message-kind", 400);
        }
        var rendered = pattern.replace("%s", reference.trim());
        requireSms(rendered);
        return rendered;
    }

    public static void requireSms(String body) {
        if (!SmsText.valid(body)) {
            throw new ReportRejected("message-length", 400);
        }
    }

    private static boolean controls(String body) { return body.chars().anyMatch(Character::isISOControl); }
}
