package cd.gouv.finances.signalements.ocr.mistral;

import cd.gouv.finances.signalements.ocr.DocumentField;
import java.util.ArrayList;
import java.util.Arrays;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public final class DocumentSchema {

    public static final String PROMPT =
            "Read only what is visible on the document. "
                    + "If a value is missing or unreadable, use null. "
                    + "Do not invent a number, amount, tax id, or code. "
                    + "Do not decide fraud or tax validity. "
                    + "documentDate uses YYYY-MM-DD when it is readable. "
                    + "totalAmount is copied as written, without conversion. "
                    + "documentType is one of invoice, receipt, ticket, other, unreadable.";

    private DocumentSchema() {
    }

    public static ObjectNode format(ObjectMapper mapper) {
        var properties = mapper.createObjectNode();
        var required = mapper.createArrayNode();
        for (var field : DocumentField.values()) {
            properties.set(field.nameKey(), textOrNull(mapper, field));
            required.add(field.nameKey());
        }
        var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.set("properties", properties);
        schema.set("required", required);

        var jsonSchema = mapper.createObjectNode();
        jsonSchema.put("name", "document");
        jsonSchema.put("strict", true);
        jsonSchema.set("schema", schema);

        var format = mapper.createObjectNode();
        format.put("type", "json_schema");
        format.set("json_schema", jsonSchema);
        return format;
    }

    public static boolean coversEveryField(ObjectNode format) {
        var expected = Arrays.stream(DocumentField.values()).map(DocumentField::nameKey).sorted().toList();
        var found = new ArrayList<>(format.path("json_schema").path("schema").path("properties").propertyNames());
        found.sort(String::compareTo);
        return expected.equals(found);
    }

    private static ObjectNode textOrNull(ObjectMapper mapper, DocumentField field) {
        var node = mapper.createObjectNode();
        node.set("type", mapper.createArrayNode().add("string").add("null"));
        if (field == DocumentField.DOCUMENT_TYPE) {
            node.set(
                    "enum",
                    mapper.createArrayNode()
                            .add("invoice")
                            .add("receipt")
                            .add("ticket")
                            .add("other")
                            .add("unreadable")
                            .addNull());
        }
        return node;
    }
}
