package cd.gouv.finances.signalements.sms;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/** An explicit opt-in runs exactly one real request to the authorized test number. */
@EnabledIfEnvironmentVariable(named = "UNIKRON_LIVE_TEST", matches = "0827243119|0852214059")
class UnikronLiveTest {
    @Test
    void submitsOneSmsToTheAuthorizedDevelopmentPhone() {
        var properties = new UnikronProperties(System.getenv("UNIKRON_ENDPOINT"), System.getenv("UNIKRON_USER_NAME"),
                System.getenv("UNIKRON_APP_KEY"), System.getenv("UNIKRON_SENDER_ID"), UnikronProperties.Mode.DEVELOPMENT);
        assertThat(properties.configured()).as("Unikron configuration present").isTrue();
        var configuration = new UnikronConfiguration();
        var builder = RestClient.builder();
        if (System.getenv("UNIKRON_LIVE_DIAGNOSTIC_PATH") != null) builder.requestInterceptor((request, body, execution) -> {
            var response = execution.execute(request, body);
            var diagnostic = System.getenv("UNIKRON_LIVE_DIAGNOSTIC_PATH");
            if (diagnostic != null && !response.getStatusCode().is2xxSuccessful()) {
                var text = new String(response.getBody().readNBytes(8192), StandardCharsets.UTF_8)
                        .replace(properties.appKey(), "[redacted]").replace(properties.username(), "[redacted]");
                var path = Path.of(diagnostic);
                if (!Files.exists(path)) Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                Files.writeString(path, text);
            }
            return response;
        });
        var gateway = new UnikronSmsGateway(properties, configuration.unikronRestClient(builder), configuration.unikronCircuitBreaker(), new ObjectMapper());
        var result = gateway.send(System.getenv("UNIKRON_LIVE_TEST"), "Test SMS - plateforme de signalements du Ministere des Finances.");
        System.out.println("Unikron live test: status=" + result.status() + ", code=" + result.code() + ", http=" + result.httpStatus());
        assertThat(result.status()).isEqualTo("accepted");
    }
}
