package cd.gouv.finances.signalements.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record PlatformProperties(String frontendOrigin, boolean documentationOpen) {
}
