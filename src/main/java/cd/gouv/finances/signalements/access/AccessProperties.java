package cd.gouv.finances.signalements.access;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.access")
public record AccessProperties(String adminUsername, String adminPassword) {
}
