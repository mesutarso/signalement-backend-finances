package cd.gouv.finances.signalements.report.letter;

import cd.gouv.finances.signalements.report.entity.LetterLine;
import cd.gouv.finances.signalements.report.export.CsvRows;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class LetterAnnexDocument {

    private static final Map<String, String> FINDINGS = Map.ofEntries(
            Map.entry("non-normalized", "Facture non normalisée"),
            Map.entry("nonconform", "Facture non conforme à l’achat réel"),
            Map.entry("vat-unregistered", "TVA facturée par une entreprise non assujettie"),
            Map.entry("verification", "Vérification requise"),
            Map.entry("unknown-seller", "Vendeur non identifié"),
            Map.entry("fake-invoice", "Facture ou code non authentique"),
            Map.entry("understated", "Facture minorée"),
            Map.entry("missing-transmission", "Défaut d'enregistrement ou de transmission"),
            Map.entry("refusal", "Refus de facture"),
            Map.entry("fake-normalization", "Facture faussement normalisée"),
            Map.entry("vat-without-normalization", "TVA collectée sans facture normalisée"),
            Map.entry("registered-non-normalized", "Entreprise assujettie ne délivrant pas de facture normalisée"),
            Map.entry("exceptional-validation", "Validation exceptionnelle"));
    private static final Map<String, String> PROOFS = Map.of(
            "photo", "Photo suffisante",
            "register", "Vérification manuelle",
            "pending", "Confirmation attendue",
            "authority", "Réponse de la DGI");
    private static final Map<String, String> DECISIONS = Map.of(
            "confirm", "Constat retenu",
            "wait", "En attente de vérification");

    private LetterAnnexDocument() {
    }

    public static byte[] csv(String annex, List<LetterLine> lines) {
        var selected = lines.stream().filter(line -> annex.equals(line.getAnnex())).toList();
        var builder = new StringBuilder("\uFEFF");
        if (LetterLine.COMPANY.equals(annex)) {
            builder.append("Numéro d'impôt,Nom,Constat,Preuve,Décision\n");
            for (var line : selected) {
                CsvRows.append(builder, ',', line.getTaxId(), line.getSellerName(), findingLabel(line.getFinding()),
                        label(PROOFS, line.getProof()), label(DECISIONS, line.getDecision()));
            }
        } else {
            builder.append("Numéro d'impôt,Numéro de pièce,Date,Montant,Constat\n");
            for (var line : selected) {
                CsvRows.append(builder, ',', line.getTaxId(), line.getDocumentNumber(),
                        line.getIssuedOn() == null ? "" : line.getIssuedOn().toString(), line.getAmount(), findingLabel(line.getFinding()));
            }
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String label(Map<String, String> labels, String code) {
        if (code == null) {
            return "";
        }
        return labels.getOrDefault(code, code);
    }

    public static String findingLabel(String finding) {
        return label(FINDINGS, finding);
    }
}
