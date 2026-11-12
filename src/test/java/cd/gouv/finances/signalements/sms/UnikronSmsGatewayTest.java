package cd.gouv.finances.signalements.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class UnikronSmsGatewayTest {
    private static final String ENDPOINT = "https://sms.example.invalid/send";
    private static UnikronProperties properties() {
        return new UnikronProperties(ENDPOINT, "fixture-user", "fixture-key", null, null);
    }

    @Test
    void sendsThePhpContractAsJsonAndStripsPhoneFormatting() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"username":"fixture-user","app_key":"fixture-key","tel":"243827243119",
                         "message":"Message de recette","from":"FACNO RDC"}
                        """))
                .andRespond(withStatus(HttpStatus.ACCEPTED));
        var gateway = new UnikronSmsGateway(properties(), builder.build(), new UnikronConfiguration().unikronCircuitBreaker(), new ObjectMapper());
        assertThat(gateway.send("+082 724-31-19", "Message de recette").status()).isEqualTo("accepted");
        server.verify();
        assertThat(properties().toString()).doesNotContain("fixture-user", "fixture-key", ENDPOINT);
    }

    @Test
    void blocksOtherRecipientsBeforeAnyHttpCallEvenWithMissingCredentials() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var circuit = new UnikronConfiguration().unikronCircuitBreaker();
        var gateway = new UnikronSmsGateway(properties(), builder.build(), circuit, new ObjectMapper());
        assertThat(gateway.send("0990000000", "Message de recette").code()).isEqualTo("recipient-blocked");
        assertThat(gateway.send(null, "Message de recette").code()).isEqualTo("recipient-blocked");
        var absent = new UnikronSmsGateway(new UnikronProperties(null, null, null, null, null), builder.build(), circuit, new ObjectMapper());
        assertThat(absent.send(UnikronProperties.TEST_PHONE, "Test").code()).isEqualTo("not-configured");
        assertThat(circuit.getMetrics().getNumberOfBufferedCalls()).isZero();
        assertThat(properties().permitted("243827243119")).isTrue();
        assertThat(properties().permitted(UnikronProperties.cleanPhone("00243827243119"))).isTrue();
        assertThat(properties().permitted(UnikronProperties.cleanPhone("0852214059"))).isTrue();
        assertThat(properties().permitted(UnikronProperties.cleanPhone("+243 852 214 059"))).isTrue();
        assertThat(gateway.send(UnikronProperties.TEST_PHONE, "x".repeat(161)).code()).isEqualTo("invalid-message");
        assertThat(new UnikronProperties(ENDPOINT, "fixture-user", "fixture-key", "x".repeat(12), null).configured()).isFalse();
        server.verify();
    }

    @Test
    void opensAfterProviderFailuresThenRecoversWithOneProbe() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(times(5), requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess());
        var circuit = new UnikronConfiguration().unikronCircuitBreaker();
        var gateway = new UnikronSmsGateway(properties(), builder.build(), circuit, new ObjectMapper());
        for (int i = 0; i < 5; i++) assertThat(gateway.send(UnikronProperties.TEST_PHONE, "Test").status()).isEqualTo("unknown");
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(gateway.send(UnikronProperties.TEST_PHONE, "Test").code()).isEqualTo("circuit-open");
        circuit.transitionToHalfOpenState();
        assertThat(gateway.send(UnikronProperties.TEST_PHONE, "Test").status()).isEqualTo("accepted");
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        server.verify();
    }

    @Test
    void recordsAnUncertainNetworkOutcomeWithoutRetrying() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(ENDPOINT)).andRespond(request -> { throw new IOException("Synthetic timeout"); });
        var gateway = new UnikronSmsGateway(properties(), builder.build(), new UnikronConfiguration().unikronCircuitBreaker(), new ObjectMapper());
        assertThat(gateway.send(UnikronProperties.TEST_PHONE, "Test").status()).isEqualTo("unknown");
        server.verify();
    }

    @Test
    void distinguishesARejectionFromAnUncertainFailureAndCountsRateLimits() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        var circuit = new UnikronConfiguration().unikronCircuitBreaker();
        var gateway = new UnikronSmsGateway(properties(), builder.build(), circuit, new ObjectMapper());
        var rejection = gateway.send(UnikronProperties.TEST_PHONE, "Test");
        assertThat(rejection.status()).isEqualTo("rejected");
        assertThat(rejection.storedStatus()).isEqualTo("failed");
        assertThat(gateway.send(UnikronProperties.TEST_PHONE, "Test").code()).isEqualTo("rate-limited");
        assertThat(circuit.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        server.verify();
    }
}
