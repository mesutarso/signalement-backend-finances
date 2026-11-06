package cd.gouv.finances.signalements.ocr.mistral;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ocr.mistral")
public record MistralOcrProperties(String baseUrl, String apiKey, String model, Duration timeout) {
}
