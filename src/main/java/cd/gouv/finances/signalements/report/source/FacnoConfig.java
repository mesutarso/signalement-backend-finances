package cd.gouv.finances.signalements.report.source;

import cd.gouv.finances.signalements.report.ReportProperties;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class FacnoConfig {

    @Bean
    RestClient facnoRestClient(ReportProperties properties) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.facno().timeout())
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.facno().timeout());
        return RestClient.builder()
                .baseUrl(properties.facno().baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
