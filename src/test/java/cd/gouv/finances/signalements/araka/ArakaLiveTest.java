package cd.gouv.finances.signalements.araka;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** Explicit opt-in: only virtual funds on the fixed UAT host and the supplied test numbers. */
@EnabledIfEnvironmentVariable(named = "ARAKAPAY_LIVE_TEST", matches = "sandbox-matrix")
class ArakaLiveTest {
    @Test void exercisesTheSixteenProvidedSandboxCasesAndReconcilesEachReference() throws Exception {
        var mapper = new ObjectMapper();
        var config = new ArakaConfiguration();
        var properties = new ArakaProperties(URI.create("https://"+ArakaProperties.SANDBOX_HOST),
                System.getenv("ARAKAPAY_EMAIL"), System.getenv("ARAKAPAY_PASSWORD"), ArakaProperties.Mode.SANDBOX, false);
        var gateway = new ArakaGateway(properties, config.arakaRestClient(RestClient.builder()), config.arakaCircuitBreaker(), mapper, Clock.systemUTC());
        var results = new ArrayList<Map<String,Object>>();
        for (var entry : Map.of("MPESA","81","ORANGE","84","AIRTEL","97","AFRIMONEY","90").entrySet()) {
            for (int i=1;i<=4;i++) {
                var reference = "FT"+UUID.randomUUID().toString().replace("-","").substring(0,18);
                var wallet = "+243"+entry.getValue()+"000000"+i;
                var sent = gateway.send(reference, new BigDecimal("5000.00"), wallet, entry.getKey());
                var found = gateway.lookup(reference, sent.transactionId());
                var expected = i<=2 ? "paid" : "failed";
                var row = Map.<String,Object>of("provider",entry.getKey(),"wallet",wallet,"reference",reference,
                        "sentState",sent.state(),"sentCode",sent.code(),"lookupState",found.state(),"lookupCode",found.code(),"expected",expected);
                results.add(row);
                if (System.getenv("ARAKAPAY_RECEIPT_PATH") != null) mapper.writeValue(Path.of(System.getenv("ARAKAPAY_RECEIPT_PATH")).toFile(),results);
                System.out.println("ARAKA SANDBOX provider="+entry.getKey()+" case="+i+" sent="+sent.state()+" lookup="+found.state());
            }
        }
        for (var row : results) {
            assertThat(row.get("sentState")).as(row.get("provider")+" "+row.get("wallet")).isEqualTo(row.get("expected"));
            assertThat(row.get("lookupState")).as("Lookup "+row.get("provider")+" "+row.get("wallet")).isEqualTo(row.get("expected"));
        }
    }
}
