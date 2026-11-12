package cd.gouv.finances.signalements.araka;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import tools.jackson.databind.JsonNode;

/** The reference lookup returns an array; the ID lookup uses 'status', not 'statusDescription'. */
final class ArakaResponse {
    private ArakaResponse() { }

    static ArakaResult parse(int http, JsonNode body, String reference, String expectedId, boolean inquiry) {
        if (http == 401 || http == 403) return inquiry ? ArakaResult.unknown("authentication") : ArakaResult.failed("authentication");
        if (http >= 500 || http == 429) return new ArakaResult("unknown", "provider-unavailable", null, true);
        if (body == null || http < 200 || (http >= 300 && http != 400)) return ArakaResult.unknown("unconfirmed");
        var candidates = new ArrayList<JsonNode>();
        if (body.isArray()) body.forEach(candidates::add); else candidates.add(body);
        var matches = new ArrayList<JsonNode>();
        for (var item : candidates) {
            var origin = text(item, "originatingTransactionId");
            var own = text(item, "transactionReference");
            var id = text(item, "transactionId");
            if (origin != null && !reference.equals(origin) || own != null && !reference.equals(own)) continue;
            if (inquiry && expectedId == null && origin == null && own == null) continue;
            matches.add(item);
        }
        if (matches.size() != 1) return ArakaResult.unknown(matches.size() > 1 ? "ambiguous-reference" : "reference-unconfirmed");
        var item = matches.getFirst();
        if (expectedId != null && !expectedId.equals(text(item, "transactionId"))) return ArakaResult.unknown("reference-unconfirmed");
        var code = text(item, "statusCode");
        var status = text(item, "statusDescription");
        if (status == null) status = text(item, "status");
        status = status == null ? "" : status.toUpperCase(Locale.ROOT);
        var id = text(item, "transactionId");
        if ("200".equals(code) && List.of("APPROVED", "SUCCESS").contains(status) && id != null && http < 300)
            return new ArakaResult("paid", "approved", id, false);
        if ("400".equals(code) && List.of("FAILED", "TRANSACTION FAILED", "DECLINED", "REJECTED").contains(status))
            return new ArakaResult("failed", "declined", id, false);
        if ("202".equals(code) || List.of("PENDING", "ACCEPTED", "PROCESSING").contains(status))
            return new ArakaResult("pending", "pending", id, false);
        return new ArakaResult("unknown", "unconfirmed", id, false);
    }

    static String text(JsonNode node, String name) {
        var field = node.path(name);
        if (!field.isValueNode() || field.isNull()) return null;
        var text = field.asText().trim();
        return text.isEmpty() || text.length() > 180 ? null : text;
    }
}
