package cd.gouv.finances.signalements.ocr.herdys;

import cd.gouv.finances.signalements.ocr.EngineResponse;
import cd.gouv.finances.signalements.ocr.EngineUnavailable;
import cd.gouv.finances.signalements.ocr.port.DocumentReader;
import java.net.URI;
import java.net.http.HttpClient;
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
        var timeout = properties.timeout() == null ? Duration.ofSeconds(60) : properties.timeout();
        var httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        this.client = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public EngineResponse read(byte[] content, String mediaType) {
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()) {
            throw new EngineUnavailable("unavailable");
        }
        var body = objectMapper.createObjectNode();
        body.put("image", Base64.getEncoder().encodeToString(content));
        body.put("mediaType", mediaType);
        try {
            JsonNode response = client.post()
                    .uri(URI.create(properties.baseUrl()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, error) -> {
                        throw new EngineUnavailable("unavailable");
                    })
                    .body(JsonNode.class);
            return HerdysResponse.read(response);
        } catch (EngineUnavailable unavailable) {
            throw unavailable;
        } catch (RestClientException exception) {
            throw new EngineUnavailable("unavailable");
        }
    }
}
