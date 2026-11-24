package cd.gouv.finances.signalements.report.validation;

import java.util.List;

/** Read-only calculations over stored evidence; no fiscal lookup or decision is written here. */
public record PrecalculatedAssessment(
        String ruleVersion,
        boolean analysisComplete,
        boolean fiscalReviewRequired,
        boolean fiscalStatusMissing,
        boolean onlyFiscalStatusMissing,
        List<Criterion> criteria,
        List<String> missingFields,
        ValidationRules.Decision ifRegistered,
        ValidationRules.Decision ifUnregistered) {

    public record Criterion(String key, String label, String value, String source, String detail) { }

    public static PrecalculatedAssessment calculate(AssessmentStore.Snapshot assessment,
                                                    ValidationRules.Facts facts, boolean alreadyRewarded,
                                                    String readability, boolean markers) {
        var complete = "done".equals(assessment.jobState());
        var registered = forecast(facts, "registered", alreadyRewarded, readability, markers);
        var unregistered = forecast(facts, "unregistered", alreadyRewarded, readability, markers);
        if (!complete) {
            registered = ValidationRules.doubt("L’analyse des pièces doit être terminée avant de conclure.");
            unregistered = registered;
        }
        var fiscalReviewRequired = ValidationRules.requiresFiscalReview(facts.vatCharged(), markers);
        var missing = fiscalReviewRequired && "unknown".equals(facts.vatStatus());
        return new PrecalculatedAssessment(ValidationRules.VERSION, complete, fiscalReviewRequired, missing, false,
                List.of(new Criterion("readability", "Lisibilité de la photo", readability, "ocr",
                                "UNREADABLE".equals(readability) ? "Une nouvelle photo est nécessaire."
                                        : "READABLE".equals(readability) ? "Les éléments essentiels de la facture ont été extraits."
                                        : "PARTIAL".equals(readability) ? "La lecture est partielle ; elle ne suffit pas à déclarer la photo illisible."
                                        : "La lisibilité n’est pas encore établie. Une panne OCR n’est pas une photo illisible."),
                        normalization(assessment, markers), vat(assessment), uniqueness(assessment, facts)),
                missingFields(assessment, readability),
                registered, unregistered);
    }

    public static List<String> missingFields(AssessmentStore.Snapshot a, String readability) {
        var missing = new java.util.ArrayList<String>();
        if (a.photoId() == null || "UNREADABLE".equals(readability)) missing.add("Photo lisible");
        if (!present(a.sellerTaxId())) missing.add("NIF");
        if (!present(a.documentNumber())) missing.add("Numéro de facture");
        if (a.documentDate() == null) missing.add("Date de facture");
        return List.copyOf(missing);
    }

    private static ValidationRules.Decision forecast(ValidationRules.Facts facts, String vatStatus, boolean alreadyRewarded,
                                                     String readability, boolean markers) {
        return ValidationRules.assess(new ValidationRules.Facts(vatStatus, facts.normalization(),
                facts.vatCharged(), facts.uniqueness(), facts.documentPresent(), facts.taxIdPresent(),
                facts.unresolvedDuplicates()), alreadyRewarded, readability, markers);
    }

    private static Criterion normalization(AssessmentStore.Snapshot a, boolean markers) {
        if (!"unknown".equals(a.normalization())) {
            return new Criterion("normalization", "Normalisation de la facture", a.normalization(), "evidence", a.normalizationEvidence());
        }
        return new Criterion("normalization", "Normalisation de la facture",
                markers ? "markers-found" : "unknown", "ocr",
                markers ? "Éléments de normalisation repérés par OCR. Vérifiez leur conformité sur la facture."
                        : "Aucun élément de normalisation relevé par l’OCR.");
    }

    private static Criterion vat(AssessmentStore.Snapshot a) {
        return new Criterion("vatCharged", "TVA facturée", a.vatCharged(),
                "unknown".equals(a.vatCharged()) ? "none" : "evidence",
                a.vatChargeEvidence() == null ? "Le montant de TVA n’est pas exploitable dans la pièce sélectionnée." : a.vatChargeEvidence());
    }

    private static Criterion uniqueness(AssessmentStore.Snapshot a, ValidationRules.Facts facts) {
        if ("duplicate".equals(facts.uniqueness())) {
            return new Criterion("uniqueness", "Facture non déjà signalée", "duplicate", "platform", "Doublon confirmé pour ce dossier non prioritaire.");
        }
        if (facts.unresolvedDuplicates()) {
            return new Criterion("uniqueness", "Facture non déjà signalée", "unknown", "platform", "Un rapprochement automatique reste à trancher.");
        }
        return new Criterion("uniqueness", "Facture non déjà signalée", a.uniqueness(),
                "unknown".equals(a.uniqueness()) ? "platform" : "review",
                "unknown".equals(a.uniqueness())
                        ? "Aucun rapprochement ouvert. L’antériorité reste à confirmer ; l’absence d’alerte n’est pas une preuve d’unicité."
                        : a.uniquenessEvidence());
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }
}
