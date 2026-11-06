package cd.gouv.finances.signalements.ocr.mistral;

import cd.gouv.finances.signalements.ocr.DocumentField;
import cd.gouv.finances.signalements.ocr.EngineResponse;
import cd.gouv.finances.signalements.ocr.EngineUnavailable;
import cd.gouv.finances.signalements.ocr.ExtractedField;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class MistralResponse {

    private MistralResponse() {
    }

    public static EngineResponse read(ObjectMapper mapper, JsonNode response) {
        if (response == null || response.path("model").asText().isBlank()) {
            throw new EngineUnavailable("unexpected-response");
        }
        var annotation = annotation(mapper, response.get("document_annotation"));
        List<ExtractedField> fields = new ArrayList<>();
        for (var field : DocumentField.values()) {
            fields.add(new ExtractedField(field.nameKey(), text(annotation, field.nameKey())));
        }
        return new EngineResponse(response.path("model").asText(), List.copyOf(fields));
    }

    private static JsonNode annotation(ObjectMapper mapper, JsonNode raw) {
        if (raw == null || raw.isNull()) {
            return mapper.createObjectNode();
        }
        if (raw.isObject()) {
            return raw;
        }
        if (!raw.isTextual() || raw.asText().isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(raw.asText());
        } catch (Exception exception) {
            throw new EngineUnavailable("unexpected-response");
        }
    }

    private static String text(JsonNode annotation, String name) {
        var node = annotation.get(name);
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        var value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }
}
