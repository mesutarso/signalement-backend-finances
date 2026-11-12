package cd.gouv.finances.signalements.araka;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ArakaGateway {
    private final ArakaProperties properties;
    private final RestClient arakaRestClient;
    private final CircuitBreaker arakaCircuitBreaker;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Semaphore capacity = new Semaphore(4);
    private String token;
    private Instant tokenUntil = Instant.MIN;

    public ArakaResult send(String reference, BigDecimal amount, String wallet, String provider) {
        if (!properties.configured()) return ArakaResult.failed("not-configured");
        if (!properties.permits(wallet, provider)) return ArakaResult.failed("recipient-blocked");
        if (reference == null || !reference.matches("[A-Za-z0-9]{1,20}") || amount == null || amount.signum() <= 0
                || amount.scale() > 2 || amount.compareTo(new BigDecimal("999999.99")) > 0) return ArakaResult.failed("invalid-order");
        return guarded(() -> {
            var auth = authentication();
            if (auth == null) return ArakaResult.failed("authentication");
            var payload = Map.of("order", Map.of("transactionReference", reference, "amount", amount, "currency", "CDF"),
                    "destination", Map.of("provider", provider, "walletID", wallet));
            var response = request("/api/Pay/sendmobilemoney", payload, auth);
            if (response.http() == 401) invalidate(auth);
            return ArakaResponse.parse(response.http(), response.body(), reference, null, false);
        }, false);
    }

    public ArakaResult lookup(String reference, String transactionId) {
        if (!properties.configured()) return ArakaResult.unknown("not-configured");
        return guarded(() -> {
            var auth = authentication();
            if (auth == null) return ArakaResult.unknown("authentication");
            // Always use the original reference: this also detects duplicate provider transactions.
            var response = request("/api/Reporting/transactionstatusbyreference/" + reference, null, auth);
            if (response.http() == 401) invalidate(auth);
            return ArakaResponse.parse(response.http(), response.body(), reference, transactionId, true);
        }, true);
    }

    private ArakaResult guarded(java.util.function.Supplier<ArakaResult> call, boolean inquiry) {
        if (!capacity.tryAcquire()) return inquiry ? ArakaResult.unknown("busy") : ArakaResult.failed("busy");
        try { return arakaCircuitBreaker.executeSupplier(call); }
        catch (CallNotPermittedException open) { return inquiry ? ArakaResult.unknown("circuit-open") : ArakaResult.failed("circuit-open"); }
        catch (RestClientException failure) { return ArakaResult.unknown("network-uncertain"); }
        catch (RuntimeException invalid) { return ArakaResult.unknown("invalid-response"); }
        finally { capacity.release(); }
    }

    private synchronized String authentication() {
        if (token != null && clock.instant().isBefore(tokenUntil)) return token;
        var response = request("/api/login", Map.of("emailAddress", properties.email(), "password", properties.password()), null);
        token = response.http() == 200 && response.body() != null ? response.body().path("token").asText("") : null;
        if (token == null || token.isBlank() || token.length() > 16000) { token = null; return null; }
        // Conservative cache; an HTTP 401 invalidates it without replaying the payout POST.
        tokenUntil = clock.instant().plusSeconds(240);
        return token;
    }

    private synchronized void invalidate(String used) { if (used.equals(token)) { token = null; tokenUntil = Instant.MIN; } }

    private record Response(int http, JsonNode body) { }

    private Response request(String path, Object payload, String auth) {
        var request = payload == null ? arakaRestClient.get() : arakaRestClient.post();
        var spec = request.uri(properties.baseUrl().resolve(path)).accept(MediaType.APPLICATION_JSON);
        if (auth != null) spec.header("Authorization", "Bearer " + auth);
        if (payload != null) {
            var bytes = mapper.writeValueAsBytes(payload);
            ((RestClient.RequestBodySpec) spec).contentType(MediaType.APPLICATION_JSON).contentLength(bytes.length).body(bytes);
        }
        return spec.exchange((req, response) -> {
            var bytes = response.getBody().readNBytes(65537);
            JsonNode body = null;
            if (bytes.length > 0 && bytes.length <= 65536) {
                try { body = mapper.readTree(bytes); } catch (RuntimeException invalid) { /* Never log provider bodies. */ }
            }
            return new Response(response.getStatusCode().value(), body);
        });
    }
}
