package cd.gouv.finances.signalements.ocr.herdys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cd.gouv.finances.signalements.ocr.EngineUnavailable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

class HerdysResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void aCompleteImageKeepsTheWrittenValues() throws Exception {
        var body = mapper.readTree("""
                {
                  "vendeur": "Boutique Lumière",
                  "nif": "A1234567B",
                  "date": "2026-03-12",
                  "montant": 125000,
                  "numero": "FAC-2026-0142",
                  "code": null,
                  "articles": [{ "designation": "Riz 25 kg", "montant": "75000" }],
                  "confiance": 0.86
                }
                """);

        var read = HerdysResponse.read(body);

        assertThat(read.confidence()).isEqualByComparingTo("0.86");
        assertThat(read.fields()).anyMatch(field -> field.name().equals("sellerName") && "Boutique Lumière".equals(field.value()));
        assertThat(read.fields()).anyMatch(field -> field.name().equals("sellerTaxId") && "A1234567B".equals(field.value()));
        assertThat(read.fields()).anyMatch(field -> field.name().equals("documentDate") && "2026-03-12".equals(field.value()));
        assertThat(read.fields()).anyMatch(field -> field.name().equals("totalAmount") && "125000".equals(field.value()));
        assertThat(read.fields()).anyMatch(field -> field.name().equals("documentNumber") && "FAC-2026-0142".equals(field.value()));
        assertThat(read.fields()).anyMatch(field -> field.name().equals("readCode") && field.value() == null);
        assertThat(read.articles()).containsExactly(
                new cd.gouv.finances.signalements.ocr.ExtractedArticle("Riz 25 kg", "75000"));
    }

    @Test
    void anUnreadableImageStaysAResult() throws Exception {
        var body = mapper.readTree("""
                {"vendeur":null,"nif":null,"date":null,"montant":null,"numero":null,"code":null,"articles":null,"confiance":0.1}
                """);

        var read = HerdysResponse.read(body);

        assertThat(read.articles()).isEmpty();
        assertThat(read.confidence()).isEqualByComparingTo("0.1");
        assertThat(read.fields()).allMatch(field -> field.value() == null);
    }

    @Test
    void aConfidenceOutsideTheScaleIsRejected() throws Exception {
        var body = mapper.readTree("""
                {"vendeur":null,"nif":null,"date":null,"montant":null,"numero":null,"code":null,"articles":null,"confiance":86}
                """);

        assertThatThrownBy(() -> HerdysResponse.read(body))
                .isInstanceOf(EngineUnavailable.class)
                .extracting(error -> ((EngineUnavailable) error).code())
                .isEqualTo("unexpected-response");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"erreur\":\"échec\"}",
            "{\"vendeur\":[],\"nif\":null,\"date\":null,\"montant\":null,\"numero\":null,\"code\":null}",
            "{\"vendeur\":null,\"nif\":null,\"date\":null,\"montant\":null,\"numero\":null,\"code\":null,\"articles\":[12]}"})
    void invalidProviderOutputNeverBecomesASuccessfulReading(String json) {
        assertThatThrownBy(() -> HerdysResponse.read(mapper.readTree(json)))
                .isInstanceOf(EngineUnavailable.class).hasMessage("unexpected-response");
    }
}
