package cd.gouv.finances.signalements.araka;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ArakaProperties.class)
public class ArakaConfiguration {
    @Bean RestClient arakaRestClient(RestClient.Builder builder) {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        var requests = new JdkClientHttpRequestFactory(client);
        requests.setReadTimeout(Duration.ofSeconds(30));
        return builder.requestFactory(requests).build();
    }

    @Bean CircuitBreaker arakaCircuitBreaker() {
        return CircuitBreaker.of("araka", CircuitBreakerConfig.custom().slidingWindowSize(10).minimumNumberOfCalls(5)
                .failureRateThreshold(50).waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(1).recordExceptions(RestClientException.class)
                .recordResult(value -> value instanceof ArakaResult result && result.providerFailure()).build());
    }
}
