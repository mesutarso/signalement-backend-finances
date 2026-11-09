package cd.gouv.finances.signalements.ocr.herdys;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ocr.herdys")
public record HerdysOcrProperties(String baseUrl, String apiToken, String model, Duration timeout) {

    public HerdysOcrProperties {
        model = model == null ? "mistral" : model;
        timeout = timeout == null ? Duration.ofSeconds(200) : timeout;
        if (!model.equals("mistral") && !model.equals("openai")) {
            throw new IllegalArgumentException("Le fournisseur OCR doit être mistral ou openai");
        }
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("Le délai OCR doit être compris entre 0 et 5 minutes");
        }
    }
}
