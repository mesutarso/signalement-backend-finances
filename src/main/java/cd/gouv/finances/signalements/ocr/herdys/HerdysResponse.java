package cd.gouv.finances.signalements.ocr.herdys;

import cd.gouv.finances.signalements.ocr.DocumentField;
import cd.gouv.finances.signalements.ocr.EngineResponse;
import cd.gouv.finances.signalements.ocr.EngineUnavailable;
import cd.gouv.finances.signalements.ocr.ExtractedArticle;
import cd.gouv.finances.signalements.ocr.ExtractedField;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class HerdysResponse {

    private HerdysResponse() {
    }

    public static EngineResponse read(JsonNode body) {
        return read(body, "mistral");
    }

    public static EngineResponse read(JsonNode body, String model) {
        if (body == null || !body.isObject() || body.has("erreur")) {
            throw new EngineUnavailable("unexpected-response");
        }
        for (var key : List.of("vendeur", "nif", "date", "montant", "numero", "code")) {
            if (!body.has(key)) {
                throw new EngineUnavailable("unexpected-response");
            }
        }
        List<ExtractedField> fields = List.of(
                field(body, "vendeur", DocumentField.SELLER_NAME),
                field(body, "nif", DocumentField.SELLER_TAX_ID),
                field(body, "date", DocumentField.DOCUMENT_DATE),
                field(body, "montant", DocumentField.TOTAL_AMOUNT),
                field(body, "numero", DocumentField.DOCUMENT_NUMBER),
                field(body, "code", DocumentField.READ_CODE),
                field(body, "devise", DocumentField.CURRENCY),
                field(body, "montant_ht", DocumentField.NET_AMOUNT),
                field(body, "tva", DocumentField.VAT_AMOUNT),
                field(body, "taux_tva", DocumentField.VAT_RATE),
                field(body, "montant_paye", DocumentField.PAID_AMOUNT),
                field(body, "code_def", DocumentField.DEF_CODE),
                field(body, "numero_def", DocumentField.DEF_NUMBER));
        return new EngineResponse("herdys-" + model, fields, articles(body.get("articles")), confidence(body.get("confiance")));
    }

    private static ExtractedField field(JsonNode body, String source, DocumentField field) {
        return new ExtractedField(field.nameKey(), text(body.get(source)));
    }

    private static List<ExtractedArticle> articles(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray() || node.size() > 1000) {
            throw new EngineUnavailable("unexpected-response");
        }
        var articles = new ArrayList<ExtractedArticle>();
        for (var item : node) {
            if (item == null || !item.isObject()) {
                throw new EngineUnavailable("unexpected-response");
            }
            var designation = text(item.get("designation"));
            var amount = text(item.get("montant"));
            if ((designation != null && designation.length() > 400) || (amount != null && amount.length() > 64)) {
                throw new EngineUnavailable("unexpected-response");
            }
            articles.add(new ExtractedArticle(designation, amount));
        }
        return List.copyOf(articles);
    }

    private static BigDecimal confidence(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isNumber()) {
            throw new EngineUnavailable("unexpected-response");
        }
        var value = node.decimalValue();
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new EngineUnavailable("unexpected-response");
        }
        return value;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            var value = node.asText().trim();
            if (value.length() > 4096) {
                throw new EngineUnavailable("unexpected-response");
            }
            return value.isEmpty() ? null : value;
        }
        if (node.isNumber()) {
            return node.decimalValue().stripTrailingZeros().toPlainString();
        }
        throw new EngineUnavailable("unexpected-response");
    }
}
