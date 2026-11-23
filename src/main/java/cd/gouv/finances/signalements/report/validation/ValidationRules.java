package cd.gouv.finances.signalements.report.validation;

import java.util.Map;
import java.util.UUID;

public final class ValidationRules {
    public static final String VERSION = "invoice-review-2026-11-23";
    public static final UUID AUTOMATION = UUID.fromString("00000000-0000-4000-8000-000000000044");

    public static final String VAT_WITHOUT_NORMALIZATION = "vat-without-normalization";
    public static final String DUPLICATE_MESSAGE = "Facture déjà signalée.";
    public static final String UNREADABLE_MESSAGE = "Veuillez refaire votre signalement avec une photo plus lisible.";
    static final Map<String, String> FINDINGS = Map.of(
            "fake-normalization", "Facture faussement normalisée",
            VAT_WITHOUT_NORMALIZATION, "TVA collectée sans facture normalisée",
            "registered-non-normalized", "Entreprise assujettie ne délivrant pas de facture normalisée",
            "non-normalized", "Facture non normalisée");
    static final Map<String, String> REJECTIONS = Map.of(
            "normalized", "Facture déjà normalisée.",
            "unregistered", "Entreprise non assujettie à la TVA (non éligible).",
            "unreadable", UNREADABLE_MESSAGE,
            "duplicate", DUPLICATE_MESSAGE);

    private ValidationRules() { }

    public record Facts(String vatStatus, String normalization, String vatCharged, String uniqueness,
                        boolean documentPresent, boolean taxIdPresent, boolean unresolvedDuplicates) { }
    public record Decision(String state, String finding, String reason) { }

    public static Decision assess(Facts facts, boolean alreadyRewarded, String readability, boolean normalizationMarkers) {
        if ("duplicate".equals(facts.uniqueness())) return new Decision("rejected", null, DUPLICATE_MESSAGE);
        if (!facts.documentPresent()) return doubt("Vérifiez les pièces du signalement.");
        if ("UNREADABLE".equals(readability)) return doubt("Vérifiez la lisibilité de la facture avant de décider.");
        if (facts.unresolvedDuplicates()) return doubt("Un rapprochement de factures reste à vérifier.");
        if (alreadyRewarded) return doubt("Une facture de même référence a déjà donné droit à une récompense. Vérifiez le doublon.");
        if (!"yes".equals(facts.vatCharged())) {
            return switch (facts.vatStatus()) {
                case "registered" -> doubt("Assujettissement confirmé. Vérifiez la conformité de la facture avant de décider.");
                case "unregistered" -> doubt("L’entreprise a été identifiée comme non assujettie à la TVA.");
                default -> doubt("Vérifiez si l’entreprise est assujettie à la TVA avant de décider.");
            };
        }
        if (normalizationMarkers || "normalized".equals(facts.normalization())) {
            return doubt("Vérifiez la conformité des éléments de normalisation avant de décider.");
        }
        if (!"READABLE".equals(readability)) {
            return doubt("Vérifiez les éléments de normalisation directement sur la facture.");
        }
        return new Decision("validated", VAT_WITHOUT_NORMALIZATION, FINDINGS.get(VAT_WITHOUT_NORMALIZATION) + ".");
    }

    public static String screeningReason(Facts facts, String readability) {
        if ("duplicate".equals(facts.uniqueness())) return "duplicate";
        if ("UNREADABLE".equals(readability)) return "unreadable";
        if (!"yes".equals(facts.vatCharged()) && !facts.taxIdPresent()
                && ("READABLE".equals(readability) || "PARTIAL".equals(readability))) return "identity-missing";
        return null;
    }

    static Decision doubt(String reason) {
        return new Decision("doubt", null, reason);
    }
}
