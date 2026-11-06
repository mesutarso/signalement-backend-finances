package cd.gouv.finances.signalements.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentationConfig {

    @Bean
    OpenAPI signalementsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Signalements")
                        .version("0.0.1")
                        .description("Contract of the report-processing platform."))
                .components(new Components().addSecuritySchemes(
                        "auth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("SESSION")
                                .description("Authenticated caller.")));
    }
}
