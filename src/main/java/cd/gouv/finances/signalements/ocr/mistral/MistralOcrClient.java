package cd.gouv.finances.signalements.ocr.mistral;

import cd.gouv.finances.signalements.ocr.EngineResponse;
import cd.gouv.finances.signalements.ocr.EngineUnavailable;
import cd.gouv.finances.signalements.ocr.port.DocumentReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class MistralOcrClient implements DocumentReader {

    private final RestClient mistralRestClient;
    private final MistralOcrProperties properties;
    private final ObjectMapper objectMapper;

    public MistralOcrClient(
            @Qualifier("mistralRestClient") RestClient mistralRestClient,
            MistralOcrProperties properties,
            ObjectMapper objectMapper) {
        this.mistralRestClient = mistralRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public EngineResponse read(byte[] content, String mediaType) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new EngineUnavailable("missing-key");
        }
        var body = MistralRequest.body(objectMapper, properties.model(), content, mediaType);
        try {
            JsonNode response = mistralRestClient.post()
                    .uri("/v1/ocr")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, error) -> {
                        int status = error.getStatusCode().value();
                        if (status == 401 || status == 403) {
                            throw new EngineUnavailable("rejected-key");
                        }
                        if (status == 400 || status == 422) {
                            throw new EngineUnavailable("unexpected-response");
                        }
                        throw new EngineUnavailable("unavailable");
                    })
                    .body(JsonNode.class);
            return MistralResponse.read(objectMapper, response);
        } catch (EngineUnavailable unavailable) {
            throw unavailable;
        } catch (RestClientException exception) {
            throw new EngineUnavailable("unavailable");
        }
    }
}
