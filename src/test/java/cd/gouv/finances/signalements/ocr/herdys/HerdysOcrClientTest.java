package cd.gouv.finances.signalements.ocr.herdys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cd.gouv.finances.signalements.ocr.EngineUnavailable;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class HerdysOcrClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private String url;
    private String response;
    private int status = 200;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<JsonNode> request = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        try (var stream = getClass().getResourceAsStream("/ocr/herdys-response.json")) {
            response = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ocr", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            request.set(mapper.readTree(exchange.getRequestBody()));
            var bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/ocr";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @ParameterizedTest
    @CsvSource({"mistral", "openai"})
    void sendsTheDeliveredContractWithAuthentication(String model) {
        var bytes = new byte[] {1, 2, 3};
        var read = client(model).read(bytes, "image/png");

        assertThat(authorization.get()).isEqualTo("Bearer contract-token");
        assertThat(request.get().propertyNames()).containsExactlyInAnyOrder("image", "modele");
        assertThat(request.get().path("modele").asText()).isEqualTo(model);
        assertThat(Base64.getDecoder().decode(request.get().path("image").asText())).isEqualTo(bytes);
        assertThat(read.model()).isEqualTo("herdys-" + model);
        assertThat(read.fields()).anyMatch(field -> field.name().equals("defNumber") && "0000123".equals(field.value()));
    }

    @ParameterizedTest
    @CsvSource({"400,rejected-document", "401,rejected-token", "403,rejected-token", "413,too-large",
            "415,rejected-type", "429,quota", "500,provider-error", "502,provider-error", "503,not-configured", "504,timeout"})
    void keepsTheFailureCategoryWithoutExposingTheResponse(int httpStatus, String code) {
        status = httpStatus;
        response = "{\"erreur\":\"private upstream detail\"}";
        assertThatThrownBy(() -> client("mistral").read(new byte[] {1}, "image/jpeg"))
                .isInstanceOf(EngineUnavailable.class).hasMessage(code);
    }

    @ParameterizedTest
    @CsvSource({"'{}'", "'{\"erreur\":\"failed\"}'", "'<html>error</html>'"})
    void rejectsInvalidSuccessResponses(String body) {
        response = body;
        assertThatThrownBy(() -> client("mistral").read(new byte[] {1}, "image/jpeg"))
                .isInstanceOf(EngineUnavailable.class).hasMessage("unexpected-response");
    }

    @Test
    void missingConfigurationNeverCallsTheService() {
        var client = new HerdysOcrClient(new HerdysOcrProperties(url, "", "mistral", null), mapper);
        assertThatThrownBy(() -> client.read(new byte[] {1}, "image/jpeg"))
                .isInstanceOf(EngineUnavailable.class).hasMessage("not-configured");
        assertThat(request.get()).isNull();
    }

    @Test
    void aSlowServiceBecomesARetryableFailure() throws Exception {
        var release = new CountDownLatch(1);
        server.removeContext("/ocr");
        server.createContext("/ocr", exchange -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        var client = new HerdysOcrClient(new HerdysOcrProperties(url, "contract-token", "mistral", Duration.ofMillis(100)), mapper);
        try {
            assertThatThrownBy(() -> client.read(new byte[] {1}, "image/jpeg"))
                    .isInstanceOf(EngineUnavailable.class).hasMessage("timeout");
        } finally {
            release.countDown();
        }
    }

    private HerdysOcrClient client(String model) {
        return new HerdysOcrClient(new HerdysOcrProperties(url, "contract-token", model, Duration.ofSeconds(2)), mapper);
    }
}
