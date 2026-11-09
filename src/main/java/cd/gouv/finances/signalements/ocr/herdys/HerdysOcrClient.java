package cd.gouv.finances.signalements.ocr.herdys;

import cd.gouv.finances.signalements.ocr.EngineResponse;
import cd.gouv.finances.signalements.ocr.EngineUnavailable;
import cd.gouv.finances.signalements.ocr.port.DocumentReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Base64;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class HerdysOcrClient implements DocumentReader {

    private final HerdysOcrProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient client;

    public HerdysOcrClient(HerdysOcrProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        var timeout = properties.timeout();
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        this.client = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public EngineResponse read(byte[] content, String mediaType) {
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()
                || properties.apiToken() == null || properties.apiToken().isBlank()) {
            throw new EngineUnavailable("not-configured");
        }
        var body = objectMapper.createObjectNode();
        body.put("image", Base64.getEncoder().encodeToString(content));
        body.put("modele", properties.model());
        try {
            JsonNode response = client.post()
                    .uri(URI.create(properties.baseUrl()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.apiToken())
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, error) -> {
                        throw new EngineUnavailable(errorCode(error.getStatusCode().value()));
                    })
                    .body(JsonNode.class);
            return HerdysResponse.read(response, properties.model());
        } catch (EngineUnavailable unavailable) {
            throw unavailable;
        } catch (RestClientException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof HttpTimeoutException || cause instanceof java.net.SocketTimeoutException) {
                    throw new EngineUnavailable("timeout");
                }
            }
            throw new EngineUnavailable(exception instanceof org.springframework.web.client.ResourceAccessException
                    ? "unavailable" : "unexpected-response");
        }
    }

    @Override
    public String cacheModel() {
        return "herdys-" + properties.model();
    }

    private static String errorCode(int status) {
        return switch (status) {
            case 401, 403 -> "rejected-token";
            case 400, 422 -> "rejected-document";
            case 413 -> "too-large";
            case 415 -> "rejected-type";
            case 429 -> "quota";
            case 503 -> "not-configured";
            case 504 -> "timeout";
            default -> "provider-error";
        };
    }
}
