package cd.gouv.finances.signalements.report.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cd.gouv.finances.signalements.araka.ArakaProperties;
import cd.gouv.finances.signalements.araka.ArakaResult;
import cd.gouv.finances.signalements.audit.repository.AuditEntryRepository;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.Prime;
import cd.gouv.finances.signalements.report.prime.BeneficiaryRegistry;
import cd.gouv.finances.signalements.report.prime.PrimeGuard;
import cd.gouv.finances.signalements.report.repository.PrimeRepository;
import cd.gouv.finances.signalements.report.validation.RewardPaymentGuard;
import java.net.URI;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

@EnabledIfEnvironmentVariable(named = "VALIDATION_JDBC_URL", matches = ".+")
class ExceptionalPaymentBatchesTest {

    @Test
    void queuesTheConfirmedBatchOnceAndOnlyDispatchesCurrentEligibleOrders() throws Exception {
        var jdbc = database();
        var actor = UUID.randomUUID();
        var primes = mock(PrimeRepository.class);
        var first = seed(jdbc, primes, actor, "EX-01", "2026-11-01", "0810000001");
        var second = seed(jdbc, primes, actor, "EX-02", "2026-11-02", "0840000001");
        var clock = Clock.systemUTC();
        var registry = new BeneficiaryRegistry(jdbc, null, null, clock);
        var properties = new ArakaProperties(URI.create("https://fixture.invalid"), "fixture", "fixture",
                ArakaProperties.Mode.PRODUCTION, true);
        var reports = new ReadExceptionRegime(jdbc, properties);
        var transferStore = new PaymentTransfers(jdbc, registry, new RewardPaymentGuard(jdbc, registry),
                new PrimeGuard(jdbc), properties, clock);
        var transfers = transactional(jdbc, transferStore, PaymentTransfers.class);
        var service = new ExceptionalPaymentBatches(jdbc, reports, registry, primes, null, null,
                mock(AuditEntryRepository.class), properties, clock);
        var batches = transactional(jdbc, service, ExceptionalPaymentBatches.class);

        var period = new ReadExceptionRegime.Period(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-02"));
        assertThat(reports.page(period, 0).items()).extracting(ReadExceptionRegime.Report::paymentState).containsExactly("ready", "ready");
        var candidates = reports.candidates(period);
        assertThat(candidates).extracting(ReadExceptionRegime.Candidate::id).containsExactly(second, first);
        var orders = List.of(order(candidates.getFirst(), "orange"), order(candidates.getLast(), "mpesa"));
        var request = new ExceptionalPaymentBatches.Request(UUID.randomUUID(), "production", "Motif de recette", orders);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = pool.invokeAll(List.<java.util.concurrent.Callable<ExceptionalPaymentBatches.Batch>>of(
                    () -> batches.authorize(request, actor), () -> batches.authorize(request, actor)));
            var expected = new ExceptionalPaymentBatches.Batch(request.requestId(), 2, 10_000);
            assertThat(results.getFirst().get()).isEqualTo(expected);
            assertThat(results.getLast().get()).isEqualTo(expected);
        }
        assertThat(reports.candidates(period)).isEmpty();
        assertThat(reports.page(period, 0).items()).extracting(ReadExceptionRegime.Report::paymentState).containsExactly("queued", "queued");
        assertThatThrownBy(() -> batches.authorize(request, UUID.randomUUID()))
                .isInstanceOfSatisfying(ReportRejected.class, e -> assertThat(e.code()).isEqualTo("payment-request-conflict"));
        assertThatThrownBy(() -> batches.authorize(new ExceptionalPaymentBatches.Request(UUID.randomUUID(),
                "production", request.reason(), orders), actor))
                .isInstanceOfSatisfying(ReportRejected.class, e -> assertThat(e.code()).isEqualTo("exception-stale"));

        var statement = jdbc.queryForObject("select id from app.payment_statement where exceptional", UUID.class);
        var line = jdbc.queryForObject("""
                select l.id from app.payment_statement_line l join app.prime p on p.id = l.prime_id where p.report_id = ?
                """, UUID.class, first);
        assertThat(new ReadPaymentStatementsQuery(jdbc).executeOne(statement).exceptional()).isTrue();
        assertThatThrownBy(() -> transfers.reserveLine(statement, line, "mpesa", UUID.randomUUID(), actor))
                .isInstanceOfSatisfying(ReportRejected.class, e -> assertThat(e.code()).isEqualTo("exception-payment-route"));

        jdbc.update("update app.report set validation_state = 'rejected' where id = ?", second);
        var claims = transfers.exceptionDue().stream().map(transfers::claimException).filter(java.util.Objects::nonNull).toList();
        assertThat(claims).hasSize(1);
        assertThat(transfers.exceptionDue()).isEmpty();
        var sent = claims.getFirst().transfer();
        assertThat(sent.lineId()).isEqualTo(line);
        transfers.submitted(sent.id(), new ArakaResult("paid", "approved", "FIXTURE", false));

        assertThat(reports.page(period, 0).items()).extracting(ReadExceptionRegime.Report::paymentState).containsExactly("unvalidated", "paid");
        assertThat(batches.authorize(request, actor).count()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from app.payment_attempt", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from app.exception_payment_item where state = 'blocked'", Integer.class)).isEqualTo(1);
    }

    private static ExceptionalPaymentBatches.Order order(ReadExceptionRegime.Candidate candidate, String operator) {
        return new ExceptionalPaymentBatches.Order(candidate.id(), candidate.lineId(), candidate.previousTransferId(),
                candidate.amount(), candidate.phone(), operator);
    }

    private static UUID seed(JdbcTemplate jdbc, PrimeRepository primes, UUID actor, String reference, String date, String phone) {
        var report = UUID.randomUUID();
        var primeId = UUID.randomUUID();
        jdbc.update("""
                insert into app.report (id, external_id, source, reference_code, reported_on, reported_at, title,
                    description, reporter_phone, imported_at, validation_state)
                values (?, ?, 'test', ?, ?::date, now(), 'Recette', 'Synthétique', ?, now(), 'validated')
                """, report, reference, reference, date, phone);
        jdbc.update("""
                insert into app.prime (id, declarant_id, phone, rule_version, threshold, amount, status, actor_id,
                    recorded_at, updated_at, report_id, reward_setting_id)
                select ?, ?, ?, 'fixed-reward', 0, 5000, 'approved', ?, now(), now(), ?, id from app.reward_setting where active
                """, primeId, reference, phone, actor, report);
        jdbc.update("insert into app.beneficiary_account values (?, ?, 'Recette', ?, now())", reference, "a".repeat(64), actor);
        jdbc.update("insert into app.payout_number values (?, ?, ?, 'ready', ?, now(), now())", UUID.randomUUID(), reference, phone, actor);
        var prime = mock(Prime.class);
        when(prime.getId()).thenReturn(primeId);
        when(prime.getStatus()).thenReturn(Prime.APPROVED);
        when(prime.getPhone()).thenReturn(phone);
        when(prime.getAmount()).thenReturn(5000);
        when(primes.findById(primeId)).thenReturn(Optional.of(prime));
        return report;
    }

    private static JdbcTemplate database() throws Exception {
        var base = System.getenv("VALIDATION_JDBC_URL");
        var user = System.getenv("VALIDATION_DB_USER");
        var password = System.getenv("VALIDATION_DB_PASSWORD");
        var name = "exception_regime_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(base, user, password); var statement = connection.createStatement()) {
            statement.execute("create database " + name);
        }
        var url = base.substring(0, base.lastIndexOf('/') + 1) + name;
        Flyway.configure().dataSource(url, user, password).load().migrate();
        return new JdbcTemplate(new DriverManagerDataSource(url, user, password));
    }

    private static <T> T transactional(JdbcTemplate jdbc, T target, Class<T> type) {
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        var advice = new TransactionInterceptor();
        advice.setTransactionManager(new JdbcTransactionManager(jdbc.getDataSource()));
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        proxy.addAdvice(advice);
        return type.cast(proxy.getProxy());
    }
}
