package cd.gouv.finances.signalements.ocr.mistral;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cd.gouv.finances.signalements.ocr.EngineUnavailable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MistralContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void theSchemaCoversEveryChainField() {
        assertThat(DocumentSchema.coversEveryField(DocumentSchema.format(mapper))).isTrue();
    }

    @Test
    void anImageLeavesWithoutReturningTheFile() {
        var body = MistralRequest.body(mapper, "mistral-ocr-latest", new byte[] {1, 2}, "image/jpeg");

        assertThat(body.path("document").path("type").asText()).isEqualTo("image_url");
        assertThat(body.path("document").path("image_url").asText()).startsWith("data:image/jpeg;base64,");
        assertThat(body.path("include_image_base64").asBoolean()).isFalse();
        assertThat(body.path("include_blocks").asBoolean()).isFalse();
        assertThat(body.has("pages")).isFalse();
    }

    @Test
    void aPdfIsLimitedToTheFirstEightPages() {
        var body = MistralRequest.body(mapper, "mistral-ocr-latest", new byte[] {1}, "application/pdf");

        assertThat(body.path("document").path("type").asText()).isEqualTo("document_url");
        assertThat(body.path("pages").asText()).isEqualTo("0-7");
    }

    @Test
    void aTextAnnotationBecomesFields() throws Exception {
        var response = mapper.readTree("""
                {"model":"mistral-ocr-latest","document_annotation":"{\\"documentNumber\\":\\"F-1\\",\\"totalAmount\\":null}"}
                """);

        var read = MistralResponse.read(mapper, response);

        assertThat(read.model()).isEqualTo("mistral-ocr-latest");
        assertThat(read.fields()).anyMatch(field -> field.name().equals("documentNumber") && "F-1".equals(field.value()));
        assertThat(read.fields()).anyMatch(field -> field.name().equals("totalAmount") && field.value() == null);
        assertThat(read.fields()).anyMatch(field -> field.name().equals("sellerTaxId") && field.value() == null);
    }

    @Test
    void anUnreadableAnnotationIsAnUnexpectedResponse() {
        var response = mapper.createObjectNode();
        response.put("model", "mistral-ocr-latest");
        response.put("document_annotation", "{");

        assertThatThrownBy(() -> MistralResponse.read(mapper, response))
                .isInstanceOf(EngineUnavailable.class)
                .extracting(error -> ((EngineUnavailable) error).code())
                .isEqualTo("unexpected-response");
    }
}
