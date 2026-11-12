package cd.gouv.finances.signalements.report.validation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import cd.gouv.finances.signalements.report.prime.BeneficiaryRegistry;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named = "VALIDATION_JDBC_URL", matches = ".+")
class ValidationMigrationTest {
    @Test
    void upgradesExistingDecisionsWithoutConvertingThemAndEnforcesUniqueFixedRewards() throws Exception {
        var base = System.getenv("VALIDATION_JDBC_URL");
        var user = System.getenv("VALIDATION_DB_USER");
        var password = System.getenv("VALIDATION_DB_PASSWORD");
        var name = "validation_migration_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(base, user, password); var statement = connection.createStatement()) {
            statement.execute("create database " + name);
        }
        var url = base.substring(0, base.lastIndexOf('/') + 1) + name;
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        Flyway.configure().dataSource(url, user, password).target("43").load().migrate();
        var historical = UUID.randomUUID();
        var pending = UUID.randomUUID();
        insertReport(jdbc, historical, "LEGACY");
        insertReport(jdbc, pending, "PENDING");
        jdbc.update("insert into app.qualification (id,report_id,decision,finding,proof,justification,actor_id,recorded_at) values (?,?, 'confirm','non-normalized','photo','Preuve historique synthétique',?,now())", UUID.randomUUID(), historical, UUID.randomUUID());
        jdbc.update("insert into app.prime (id,declarant_id,phone,rule_version,threshold,amount,status,actor_id,recorded_at,updated_at) values (?,'legacy-person','0990000000','cdc-2026-10-28',30,10000,'proposed',?,now(),now())", UUID.randomUUID(), UUID.randomUUID());
        var flyway = Flyway.configure().dataSource(url, user, password).load();
        flyway.migrate();
        flyway.validate();
        assertThat(jdbc.queryForObject("select validation_state from app.report where id = ?", String.class, historical)).isEqualTo("legacy");
        assertThat(jdbc.queryForObject("select count(*) from app.report_assessment where report_id = ?", Integer.class, historical)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from app.report_assessment where report_id = ? and job_state='queued'", Integer.class, pending)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select amount from app.prime where declarant_id='legacy-person'", Integer.class)).isEqualTo(10000);
        assertThat(jdbc.queryForObject("select amount from app.reward_setting where active", Integer.class)).isEqualTo(5000);
        insertFixed(jdbc, pending, "invoice-one");
        assertThatThrownBy(() -> insertFixed(jdbc, pending, "invoice-two")).hasRootCauseInstanceOf(SQLException.class);
        var another = UUID.randomUUID();
        insertReport(jdbc, another, "ANOTHER");
        assertThatThrownBy(() -> insertFixed(jdbc, another, "invoice-one")).hasRootCauseInstanceOf(SQLException.class);
        assertThat(jdbc.queryForObject("select count(*) from app.prime where report_id is not null", Integer.class)).isEqualTo(1);
        verifyDurableJobs(jdbc);
    }

    private static void verifyDurableJobs(JdbcTemplate jdbc) {
        jdbc.update("update app.report_assessment set job_state='done'");
        var reportId = UUID.randomUUID();
        insertReport(jdbc, reportId, "LEASE");
        var beneficiaries = mock(BeneficiaryRegistry.class);
        var rewards = mock(FixedRewards.class);
        var jobs = new AnalysisJobs(jdbc, Clock.systemUTC(), beneficiaries, rewards);
        var tx = new TransactionTemplate(new JdbcTransactionManager(jdbc.getDataSource()));
        tx.executeWithoutResult(status -> jobs.enqueue(reportId, true));
        var expired = tx.execute(status -> jobs.claim().orElseThrow());
        jdbc.update("update app.report_assessment set lease_until=now()-interval '1 second' where report_id=?", reportId);
        var current = tx.execute(status -> jobs.claim().orElseThrow());
        assertThat(current.leaseId()).isNotEqualTo(expired.leaseId());
        var evidence = mock(AssessmentEvidence.class);
        var decision = mock(AssessmentDecision.class);
        var complete = new CompleteAnalysis(jdbc, new AssessmentStore(jdbc, new ObjectMapper()), evidence, decision, beneficiaries);
        tx.executeWithoutResult(status -> complete.execute(expired));
        tx.executeWithoutResult(status -> jobs.failed(expired, "stale-owner"));
        assertThat(jobs.renew(expired)).isFalse();
        assertThat(jobs.renew(current)).isTrue();
        verifyNoInteractions(evidence, decision);
        tx.executeWithoutResult(status -> jobs.enqueue(reportId, true));
        tx.executeWithoutResult(status -> complete.execute(current));
        assertThat(jdbc.queryForObject("select job_state from app.report_assessment where report_id=?", String.class, reportId)).isEqualTo("queued");
        verifyNoInteractions(evidence, decision);
        for (int attempt = 1; attempt <= 3; attempt++) {
            jdbc.update("update app.report_assessment set available_at=now() where report_id=?", reportId);
            var retry = tx.execute(status -> jobs.claim().orElseThrow());
            tx.executeWithoutResult(status -> jobs.failed(retry, "synthetic-failure"));
        }
        assertThat(jdbc.queryForObject("select job_state from app.report_assessment where report_id=?", String.class, reportId)).isEqualTo("done");
        assertThat(jdbc.queryForObject("select validation_state from app.report where id=?", String.class, reportId)).isEqualTo("doubt");
        assertThat(jdbc.queryForObject("select count(*) from app.prime where report_id=?", Integer.class, reportId)).isZero();
    }

    private static void insertReport(JdbcTemplate jdbc, UUID id, String reference) {
        jdbc.update("insert into app.report (id,external_id,source,reference_code,reported_on,reported_at,title,description,imported_at) values (?,?, 'test',?,current_date,current_timestamp,'Recette','Données synthétiques',now())", id, reference, reference);
    }
    private static void insertFixed(JdbcTemplate jdbc, UUID report, String key) {
        jdbc.update("""
                insert into app.prime (id,declarant_id,phone,rule_version,threshold,amount,status,actor_id,recorded_at,updated_at,report_id,reward_setting_id,invoice_key)
                select ?,'fixed-person',null,'fixed-reward',0,amount,'proposed',?,now(),now(),?,id,? from app.reward_setting where active
                """, UUID.randomUUID(), UUID.randomUUID(), report, key);
    }
}
