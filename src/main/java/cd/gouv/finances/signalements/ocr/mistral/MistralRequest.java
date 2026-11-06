package cd.gouv.finances.signalements.ocr.mistral;

import java.util.Base64;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public final class MistralRequest {

    private MistralRequest() {
    }

    public static ObjectNode body(ObjectMapper mapper, String model, byte[] content, String mediaType) {
        var document = mapper.createObjectNode();
        var data = "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(content);
        if ("application/pdf".equals(mediaType)) {
            document.put("type", "document_url");
            document.put("document_url", data);
        } else {
            document.put("type", "image_url");
            document.put("image_url", data);
        }

        var body = mapper.createObjectNode();
        body.put("model", model);
        body.set("document", document);
        body.set("document_annotation_format", DocumentSchema.format(mapper));
        body.put("document_annotation_prompt", DocumentSchema.PROMPT);
        body.put("include_image_base64", false);
        body.put("include_blocks", false);
        if ("application/pdf".equals(mediaType)) {
            body.put("pages", "0-7");
        }
        return body;
    }
}
