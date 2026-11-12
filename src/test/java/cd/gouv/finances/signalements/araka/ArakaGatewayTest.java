package cd.gouv.finances.signalements.araka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class ArakaGatewayTest {
    private static final String BASE = "https://araka-api-uat.azurewebsites.net";
    private final ArakaProperties properties = new ArakaProperties(URI.create(BASE), "fixture-email", "fixture-password", null, false);

    private ArakaGateway gateway(RestClient.Builder builder) {
        return new ArakaGateway(properties, builder.build(), new ArakaConfiguration().arakaCircuitBreaker(), new ObjectMapper(), Clock.systemUTC());
    }

    @Test void sendsTheDocumentedCdfContractWithInternationalWalletAndBearer() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/api/login")).andExpect(content().json("{\"emailAddress\":\"fixture-email\",\"password\":\"fixture-password\"}"))
                .andRespond(withSuccess("{\"token\":\"fixture-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/api/Pay/sendmobilemoney")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer fixture-token"))
                .andExpect(content().json("""
                        {"order":{"transactionReference":"FC1","amount":5000.00,"currency":"CDF"},
                        "destination":{"provider":"MPESA","walletID":"+243810000001"}}
                        """))
                .andRespond(withSuccess("{\"statusCode\":200,\"statusDescription\":\"APPROVED\",\"transactionId\":\"T1\",\"originatingTransactionId\":\"FC1\"}", MediaType.APPLICATION_JSON));
        var client = gateway(builder);
        assertThat(client.send("FC1", new BigDecimal("5000.00"), "+243810000001", "MPESA").state()).isEqualTo("paid");
        server.verify();
        assertThat(properties.toString()).doesNotContain("fixture-email", "fixture-password");
    }

    @Test void blocksRealNumbersMismatchedOperatorsAndUnapprovedProductionBeforeHttp() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        assertThat(gateway(builder).send("FC1", BigDecimal.ONE, "+243827243119", "MPESA").code()).isEqualTo("recipient-blocked");
        assertThat(gateway(builder).send("FC1", BigDecimal.ONE, "+243840000001", "MPESA").code()).isEqualTo("recipient-blocked");
        assertThat(new ArakaProperties(URI.create("https://production.example.invalid"), "u", "p", ArakaProperties.Mode.SANDBOX, false).configured()).isFalse();
        assertThat(new ArakaProperties(URI.create("https://production.example.invalid"), "u", "p", ArakaProperties.Mode.PRODUCTION, false).configured()).isFalse();
        assertThat(ArakaProperties.wallet("0810000001")).isEqualTo("+243810000001");
        for (var prefix : new String[]{"81", "84", "97", "90"}) for (int i=1; i<=4; i++)
            assertThat(ArakaProperties.wallet("+243"+prefix+"000000"+i)).isNotNull();
        server.verify();
    }

    @Test void neverReplaysAnUnauthorizedPayoutAndRefreshesAuthenticationForALaterInquiry() {
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE+"/api/login")).andRespond(withSuccess("{\"token\":\"old\"}",MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE+"/api/Pay/sendmobilemoney")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(BASE+"/api/login")).andRespond(withSuccess("{\"token\":\"new\"}",MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE+"/api/Reporting/transactionstatusbyreference/FC1"))
                .andExpect(header("Authorization","Bearer new"))
                .andRespond(withSuccess("[{\"transactionReference\":\"FC1\",\"transactionId\":\"T1\",\"statusCode\":200,\"status\":\"APPROVED\"}]",MediaType.APPLICATION_JSON));
        var client=gateway(builder);
        assertThat(client.send("FC1",BigDecimal.ONE,"+243810000001","MPESA").state()).isEqualTo("failed");
        assertThat(client.lookup("FC1",null).state()).isEqualTo("paid");
        server.verify();
    }

    @Test void aTimeoutAfterSubmissionIsUncertainAndNeverRetried() {
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE+"/api/login")).andRespond(withSuccess("{\"token\":\"t\"}",MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE+"/api/Pay/sendmobilemoney")).andRespond(request->{throw new IOException("Synthetic timeout");});
        assertThat(gateway(builder).send("FC1",BigDecimal.ONE,"+243810000001","MPESA").state()).isEqualTo("unknown");
        server.verify();
    }

    @Test void opensTheCircuitWithoutSendingAnotherOrderAndRecoversThroughAReadOnlyProbe() {
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE+"/api/login")).andRespond(withSuccess("{\"token\":\"t\"}",MediaType.APPLICATION_JSON));
        server.expect(org.springframework.test.web.client.ExpectedCount.times(5),requestTo(BASE+"/api/Pay/sendmobilemoney"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(BASE+"/api/Reporting/transactionstatusbyreference/FC1"))
                .andRespond(withSuccess("[{\"transactionReference\":\"FC1\",\"transactionId\":\"T1\",\"statusCode\":200,\"status\":\"APPROVED\"}]",MediaType.APPLICATION_JSON));
        var circuit=new ArakaConfiguration().arakaCircuitBreaker();
        var client=new ArakaGateway(properties,builder.build(),circuit,new ObjectMapper(),Clock.systemUTC());
        for(int i=0;i<5;i++)assertThat(client.send("FC"+(i+1),BigDecimal.ONE,"+243810000001","MPESA").state()).isEqualTo("unknown");
        assertThat(circuit.getState()).isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN);
        assertThat(client.send("FC6",BigDecimal.ONE,"+243810000001","MPESA").code()).isEqualTo("circuit-open");
        circuit.transitionToHalfOpenState();
        assertThat(client.lookup("FC1",null).state()).isEqualTo("paid");
        assertThat(circuit.getState()).isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED);
        server.verify();
    }
}
