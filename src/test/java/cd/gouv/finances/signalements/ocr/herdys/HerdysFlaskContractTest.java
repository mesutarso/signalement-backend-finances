package cd.gouv.finances.signalements.ocr.herdys;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

@EnabledIfSystemProperty(named = "herdys.contract.url", matches = "http://127\\.0\\.0\\.1:[0-9]+/ocr")
class HerdysFlaskContractTest {

    @ParameterizedTest
    @ValueSource(strings = {"mistral", "openai"})
    void readsThroughTheUnmodifiedFlaskRoutes(String model) {
        var properties = new HerdysOcrProperties(System.getProperty("herdys.contract.url"), "contract-token", model, Duration.ofSeconds(5));
        var client = new HerdysOcrClient(properties, new ObjectMapper());
        var png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=");

        var reading = client.read(png, "image/png");

        assertThat(reading.model()).isEqualTo("herdys-" + model);
        assertThat(reading.confidence()).isEqualByComparingTo("0.86");
        assertThat(reading.fields()).hasSize(13);
        assertThat(reading.fields()).anyMatch(field -> field.name().equals("currency") && "USD".equals(field.value()));
        assertThat(reading.fields()).anyMatch(field -> field.name().equals("defNumber") && "0000123".equals(field.value()));
        assertThat(reading.articles()).hasSize(1);
    }
}
