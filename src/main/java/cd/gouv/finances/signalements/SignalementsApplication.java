package cd.gouv.finances.signalements;

import cd.gouv.finances.signalements.access.AccessProperties;
import cd.gouv.finances.signalements.config.PlatformProperties;
import cd.gouv.finances.signalements.report.ReportProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({PlatformProperties.class, AccessProperties.class, ReportProperties.class})
public class SignalementsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SignalementsApplication.class, args);
    }
}
