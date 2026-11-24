package cd.gouv.finances.signalements.report.query;

import static org.assertj.core.api.Assertions.assertThat;

import cd.gouv.finances.signalements.report.ReportReference;
import cd.gouv.finances.signalements.report.declarant.ReadDeclarantSituationQuery;
import cd.gouv.finances.signalements.report.rule.ActiveRule;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "VALIDATION_JDBC_URL", matches = ".+")
class EstablishmentAndDeclarantQueryTest {

    @Test
    void combinesRegisteredEstablishmentsWithFindingsAndUsesRecordedRewardAmountsForValidatedReports() throws Exception {
        var jdbc = database();
        var first = report(jdbc, "VAL-01", " account ", "validated");
        var second = report(jdbc, "VAL-02", "account", "validated");
        var rejected = report(jdbc, "REJ-01", "account", "rejected");
        var historical = report(jdbc, "HIST-01", "account", "legacy");
        var unidentified = report(jdbc, "ANON-01", null, "validated");
        report(jdbc, "ANON-02", null, "queued");
        reward(jdbc, first, "account", 5000, "proposed");
        reward(jdbc, second, "account", 7500, "approved");
        reward(jdbc, rejected, "account", 9000, "approved");
        reward(jdbc, historical, "account", 11000, "withdrawn");
        reward(jdbc, unidentified, "report:" + unidentified, 4000, "proposed");
        jdbc.update("""
                insert into app.qualification (id, report_id, decision, finding, proof, justification, seller_name, seller_tax_id, actor_id, recorded_at)
                values (?, ?, 'confirm', 'non-normalized', 'photo', 'Constat historique', 'Entreprise A', 'ab-01', ?, now())
                """, UUID.randomUUID(), historical, UUID.randomUUID());
        jdbc.update("insert into app.report_assessment (report_id, seller_name, seller_tax_id) values (?, 'Entreprise A', ' AB-01 '), (?, 'Entreprise signalée', null)",
                first, second);
        jdbc.update("""
                insert into app.taxpayer (id, tax_id, name, verified, verified_at, recorded_at)
                values (?, 'ab-01', 'Registre A', true, now(), now()), (?, 'AB-02', 'Entreprise au registre', false, null, now())
                """, UUID.randomUUID(), UUID.randomUUID());

        var overview = new ReadAdministrationReports(jdbc);
        var establishments = overview.establishments(0);
        assertThat(establishments.total()).isEqualTo(3);
        assertThat(establishments.items()).satisfiesExactly(
                merged -> {
                    assertThat(merged.taxId()).isEqualTo("AB-01");
                    assertThat(merged.total()).isEqualTo(2);
                    assertThat(merged.registryVerified()).isTrue();
                },
                registryOnly -> {
                    assertThat(registryOnly.name()).isEqualTo("Entreprise au registre");
                    assertThat(registryOnly.total()).isZero();
                    assertThat(registryOnly.registryVerified()).isFalse();
                },
                reportedOnly -> {
                    assertThat(reportedOnly.name()).isEqualTo("Entreprise signalée");
                    assertThat(reportedOnly.registryVerified()).isNull();
                });
        assertThat(overview.companies(0).total()).isEqualTo(2);

        var declarant = overview.declarants(0).items().getFirst();
        assertThat(declarant.total()).isEqualTo(4);
        assertThat(declarant.validated()).isEqualTo(3);
        assertThat(declarant.validatedRewardAmount()).isEqualTo(12_500);

        var situations = new ReadDeclarantSituationQuery(jdbc, new ReportReference(jdbc), new ActiveRule(jdbc));
        var situation = situations.execute("VAL-01");
        assertThat(situation.validatedCount()).isEqualTo(declarant.validated());
        assertThat(situation.validatedRewardAmount()).isEqualTo(declarant.validatedRewardAmount());
        assertThat(situation.reports()).anyMatch(item -> item.id().equals(first) && item.status().equals("confirmed"));
        var anonymous = situations.execute("ANON-01");
        assertThat(anonymous.validatedRewardAmount()).isEqualTo(4000);
        assertThat(anonymous.rewardAmount()).isEqualTo(4000);
        assertThat(situations.execute("ANON-02").validatedRewardAmount()).isZero();
    }

    private static JdbcTemplate database() throws Exception {
        var base = System.getenv("VALIDATION_JDBC_URL");
        var user = System.getenv("VALIDATION_DB_USER");
        var password = System.getenv("VALIDATION_DB_PASSWORD");
        var name = "establishment_query_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(base, user, password); var statement = connection.createStatement()) {
            statement.execute("create database " + name);
        }
        var url = base.substring(0, base.lastIndexOf('/') + 1) + name;
        Flyway.configure().dataSource(url, user, password).load().migrate();
        return new JdbcTemplate(new DriverManagerDataSource(url, user, password));
    }

    private static UUID report(JdbcTemplate jdbc, String reference, String reporter, String state) {
        var id = UUID.randomUUID();
        jdbc.update("""
                insert into app.report (id, external_id, source, reference_code, reported_on, reported_at,
                    title, description, imported_at, reporter_user_id, validation_state)
                values (?, ?, 'test', ?, current_date, current_timestamp, 'Recette', 'Données fictives', now(), ?, ?)
                """, id, reference, reference, reporter, state);
        return id;
    }

    private static void reward(JdbcTemplate jdbc, UUID report, String declarant, int amount, String status) {
        jdbc.update("""
                insert into app.prime (id, declarant_id, rule_version, threshold, amount, status,
                    actor_id, recorded_at, updated_at, report_id, reward_setting_id)
                select ?, ?, 'fixed-reward', 0, ?, ?, ?, now(), now(), ?, id from app.reward_setting where active
                """, UUID.randomUUID(), declarant, amount, status, UUID.randomUUID(), report);
    }
}
