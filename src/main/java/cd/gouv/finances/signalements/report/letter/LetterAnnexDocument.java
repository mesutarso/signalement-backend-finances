package cd.gouv.finances.signalements.report.letter;

import cd.gouv.finances.signalements.report.entity.LetterLine;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class LetterAnnexDocument {

    private static final Map<String, String> FINDINGS = Map.of(
            "non-normalized", "Facture non normalisée",
            "nonconform", "Facture non conforme à l’achat réel",
            "vat-unregistered", "TVA facturée par une entreprise non assujettie",
            "verification", "Vérification requise",
            "unknown-seller", "Vendeur non identifié",
            "fake-invoice", "Facture ou code non authentique",
            "understated", "Facture minorée",
            "missing-transmission", "Défaut d'enregistrement ou de transmission",
            "refusal", "Refus de facture");
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
            builder.append("Numéro d'impôt,Nom,Constat,Preuve,Décision,Référence\n");
            for (var line : selected) {
                builder.append(row(line.getTaxId(), line.getSellerName(), label(FINDINGS, line.getFinding()), label(PROOFS, line.getProof()), label(DECISIONS, line.getDecision()), line.getReportReference()));
            }
        } else {
            builder.append("Numéro d'impôt,Numéro de pièce,Date,Montant,Constat,Référence\n");
            for (var line : selected) {
                builder.append(row(line.getTaxId(), line.getDocumentNumber(), line.getIssuedOn() == null ? "" : line.getIssuedOn().toString(), line.getAmount(), label(FINDINGS, line.getFinding()), line.getReportReference()));
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

    private static String row(String... cells) {
        var builder = new StringBuilder();
        for (var index = 0; index < cells.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(cell(cells[index]));
        }
        return builder.append('\n').toString();
    }

    private static String cell(String value) {
        var text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
