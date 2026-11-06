package cd.gouv.finances.signalements.report;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.reports")
public record ReportProperties(boolean importOnStartup, Facno facno) {

    public record Facno(String baseUrl, String apiKey, Duration timeout) {
    }
}
