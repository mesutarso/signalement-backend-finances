package cd.gouv.finances.signalements.ocr.herdys;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ocr.herdys")
public record HerdysOcrProperties(String baseUrl, Duration timeout) {
}
