package cd.gouv.finances.signalements.report.validation;

import java.util.UUID;

/** The approved five-criterion policy. Unknown evidence never becomes a positive finding. */
public final class ValidationRules {
    public static final String VERSION = "five-criteria-2026-11-12";
    public static final UUID AUTOMATION = UUID.fromString("00000000-0000-4000-8000-000000000044");

    private ValidationRules() { }

    public record Facts(String vatStatus, String normalization, String purchaseConformity,
                        String vatCharged, String uniqueness, boolean documentPresent,
                        boolean identityPresent, boolean unresolvedDuplicates) { }
    public record Decision(String state, String finding, String reason) { }

    public static Decision assess(Facts facts) {
        if ("duplicate".equals(facts.uniqueness())) {
            return new Decision("rejected", null, "La facture a déjà été signalée.");
        }
        if (!facts.documentPresent()) return doubt("Une pièce exploitable est nécessaire.");
        if (!facts.identityPresent()) return doubt("Le vendeur doit être identifié pour vérifier son statut fiscal.");
        if (facts.unresolvedDuplicates()) return doubt("Un rapprochement de factures reste à vérifier.");
        if (!"unique".equals(facts.uniqueness())) return doubt("Vérifiez que la facture n’a pas déjà été signalée.");
        return switch (facts.vatStatus()) {
            case "registered" -> registered(facts);
            case "unregistered" -> switch (facts.vatCharged()) {
                case "yes" -> new Decision("validated", "vat-unregistered", "TVA facturée par une entreprise non assujettie.");
                case "no" -> new Decision("rejected", null, "L’entreprise n’est pas assujettie et la facture ne mentionne pas de TVA.");
                default -> doubt("Vérifiez si de la TVA est facturée.");
            };
            default -> doubt("L’assujettissement à la TVA doit être vérifié.");
        };
    }

    private static Decision registered(Facts facts) {
        if ("non-normalized".equals(facts.normalization())) {
            return new Decision("validated", "non-normalized", "Facture non normalisée émise par un établissement assujetti.");
        }
        if ("nonconform".equals(facts.purchaseConformity())) {
            return new Decision("validated", "nonconform", "La facture ne correspond pas à l’achat réel.");
        }
        if ("normalized".equals(facts.normalization()) && "conform".equals(facts.purchaseConformity())) {
            return new Decision("rejected", null, "Aucune des anomalies prévues par la grille n’est établie.");
        }
        return doubt("La normalisation de la facture ou sa conformité à l’achat réel reste à vérifier.");
    }

    public static Decision doubt(String reason) { return new Decision("doubt", null, reason); }
}
