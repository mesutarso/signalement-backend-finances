package cd.gouv.finances.signalements.sms;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.URI;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class UnikronSmsGateway {
    private final UnikronProperties properties;
    private final RestClient client;
    private final CircuitBreaker circuit;
    private final ObjectMapper mapper;
    private final Semaphore capacity = new Semaphore(4);

    public UnikronSmsGateway(UnikronProperties properties, @Qualifier("unikronRestClient") RestClient client,
                             @Qualifier("unikronCircuitBreaker") CircuitBreaker circuit, ObjectMapper mapper) {
        this.properties = properties;
        this.client = client;
        this.circuit = circuit;
        this.mapper = mapper;
    }

    public SmsResult send(String phone, String message) {
        var digits = UnikronProperties.cleanPhone(phone);
        if (!properties.permitted(digits)) return SmsResult.unavailable("recipient-blocked");
        if (message == null || message.isBlank() || message.length() > 160) return SmsResult.unavailable("invalid-message");
        if (!properties.configured()) return SmsResult.unavailable("not-configured");
        if (!capacity.tryAcquire()) return SmsResult.unavailable("busy");
        try {
            var body = mapper.writeValueAsBytes(new Payload(properties.username(), properties.appKey(), digits, message, properties.senderId()));
            return circuit.executeSupplier(() -> client.post().uri(URI.create(properties.endpoint()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(body.length).body(body)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (response.getStatusCode().is2xxSuccessful()) return new SmsResult("accepted", "accepted", status);
                        log.warn("SMS provider returned HTTP {}", status);
                        if (status >= 500) return new SmsResult("unknown", "provider-error", status);
                        return new SmsResult("rejected", status == 429 ? "rate-limited" : "provider-rejected", status);
                    }));
        } catch (CallNotPermittedException open) {
            return SmsResult.unavailable("circuit-open");
        } catch (RestClientException failure) {
            // A timeout may happen after acceptance: never retry an SMS automatically.
            log.warn("SMS request outcome unknown ({})", failure.getClass().getSimpleName());
            return new SmsResult("unknown", "network-error", null);
        } finally { capacity.release(); }
    }

    private record Payload(String username, @JsonProperty("app_key") String appKey, String tel,
                           String message, @JsonProperty("from") String senderId) { }
}
