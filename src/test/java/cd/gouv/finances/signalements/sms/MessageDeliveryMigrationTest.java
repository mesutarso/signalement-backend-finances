package cd.gouv.finances.signalements.sms;

import static org.assertj.core.api.Assertions.*;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.message.MessageDeliveryStore;
import cd.gouv.finances.signalements.report.message.ReadDeclarantMessagesQuery;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "VALIDATION_JDBC_URL", matches = ".+")
class MessageDeliveryMigrationTest {
    @Test
    void preservesDraftsAndOnlyOneConcurrentSenderClaimsTheMessage() throws Exception {
        var base = System.getenv("VALIDATION_JDBC_URL");
        var user = System.getenv("VALIDATION_DB_USER");
        var password = System.getenv("VALIDATION_DB_PASSWORD");
        var name = "sms_migration_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = DriverManager.getConnection(base, user, password); var s = c.createStatement()) { s.execute("create database " + name); }
        var url = base.substring(0, base.lastIndexOf('/') + 1) + name;
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        Flyway.configure().dataSource(url, user, password).target("44").load().migrate();
        var report = UUID.randomUUID();
        var message = UUID.randomUUID();
        jdbc.update("insert into app.report (id,external_id,source,reference_code,reported_on,reported_at,title,description,reporter_phone,imported_at) values (?,'SMS','test','SMS',current_date,now(),'Recette','Synthétique','+082 724 31 19',now())", report);
        jdbc.update("insert into app.declarant_message values (?,?,'SMS','situation','Message synthétique','prepared',?,now())", message, report, UUID.randomUUID());
        var flyway = Flyway.configure().dataSource(url, user, password).load();
        flyway.migrate(); flyway.validate();
        var store = new MessageDeliveryStore(jdbc, Clock.systemUTC());
        try (var workers = Executors.newFixedThreadPool(2)) {
            var results = workers.invokeAll(List.of(
                    () -> claim(store, message), () -> claim(store, message)));
            assertThat(results.stream().map(f -> { try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); } }).filter(s -> s != null)).hasSize(1);
        }
        var attempt = jdbc.queryForObject("select send_attempt_id from app.declarant_message where id=?", UUID.class, message);
        assertThat(jdbc.queryForObject("select recipient from app.declarant_message where id=?", String.class, message)).isEqualTo("243827243119");
        jdbc.update("update app.declarant_message set send_started_at=now()-interval '1 minute' where id=?", message);
        assertThat(new ReadDeclarantMessagesQuery(jdbc).execute(0).items().getFirst().status()).isEqualTo("unknown");
        assertThatThrownBy(() -> store.claim(message)).isInstanceOf(ReportRejected.class);
        store.complete(new MessageDeliveryStore.Submission(message, attempt, UnikronProperties.TEST_PHONE, "Message synthétique"), new SmsResult("accepted", "accepted", 202));
        assertThat(new ReadDeclarantMessagesQuery(jdbc).execute(0).items().getFirst().status()).isEqualTo("accepted");
        assertThatThrownBy(() -> store.claim(message)).isInstanceOf(ReportRejected.class);
    }

    private static MessageDeliveryStore.Submission claim(MessageDeliveryStore store, UUID id) {
        try { return store.claim(id); } catch (ReportRejected busy) { return null; }
    }
}
