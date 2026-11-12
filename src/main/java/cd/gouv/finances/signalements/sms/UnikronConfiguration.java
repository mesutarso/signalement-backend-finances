package cd.gouv.finances.signalements.sms;

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
@EnableConfigurationProperties(UnikronProperties.class)
public class UnikronConfiguration {
    @Bean
    RestClient unikronRestClient(RestClient.Builder builder) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(Duration.ofSeconds(10));
        return builder.requestFactory(requests).build();
    }

    @Bean
    CircuitBreaker unikronCircuitBreaker() {
        return CircuitBreaker.of("unikron", CircuitBreakerConfig.custom()
                .slidingWindowSize(10).minimumNumberOfCalls(5).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60)).permittedNumberOfCallsInHalfOpenState(1)
                .recordExceptions(RestClientException.class)
                .recordResult(result -> result instanceof SmsResult sms && sms.providerFailure()).build());
    }
}
