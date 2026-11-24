package cd.gouv.finances.signalements.report.payment;

import cd.gouv.finances.signalements.araka.ArakaProperties;
import cd.gouv.finances.signalements.audit.entity.AuditEntry;
import cd.gouv.finances.signalements.audit.repository.AuditEntryRepository;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.Prime;
import cd.gouv.finances.signalements.report.prime.BeneficiaryRegistry;
import cd.gouv.finances.signalements.report.prime.PrimeContext;
import cd.gouv.finances.signalements.report.prime.RecordPayoutCommand;
import cd.gouv.finances.signalements.report.repository.PrimeRepository;
import cd.gouv.finances.signalements.report.repository.PayoutNumberRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ExceptionalPaymentBatches {
    private final JdbcTemplate jdbc;
    private final ReadExceptionRegime reports;
    private final BeneficiaryRegistry beneficiaries;
    private final PrimeRepository primes;
    private final PayoutNumberRepository numbers;
    private final RecordPayoutCommand recordPayout;
    private final AuditEntryRepository audit;
    private final ArakaProperties araka;
    private final Clock clock;

    public record Order(@NotNull UUID id, UUID lineId, UUID previousTransferId, @Positive int amount,
            @NotBlank @Size(max = 40) String phone, @NotBlank @Size(max = 16) String operator) {
        Order normalize() {
            return new Order(id, lineId, previousTransferId, amount, phone, ArakaProperties.provider(operator));
        }
    }
    public record Request(@NotNull UUID requestId, @NotBlank @Size(max = 16) String environment,
            @NotBlank @Size(max = 500) String reason, @NotEmpty List<@NotNull @Valid Order> orders) { }
    public record Batch(UUID id, long count, long total) { }
    private record Existing(UUID actor, String fingerprint) { }

    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Transactional
    public Batch authorize(Request request, UUID actor) {
        var orders = request.orders().stream().map(Order::normalize).sorted(Comparator.comparing(Order::id)).toList();
        if (orders.stream().map(Order::id).distinct().count() != orders.size()
                || orders.stream().anyMatch(order -> order.operator() == null)) {
            throw new ReportRejected("payment-operator", 400);
        }
        var reason = request.reason().strip();
        var fingerprint = fingerprint(request.environment(), reason, orders);
        beneficiaries.lock();
        var existing = jdbc.query("select actor_id, fingerprint from app.exception_payment_batch where id = ?",
                (row, i) -> new Existing(row.getObject(1, UUID.class), row.getString(2)), request.requestId());
        if (!existing.isEmpty()) {
            var batch = existing.getFirst();
            if (!actor.equals(batch.actor()) || !fingerprint.equals(batch.fingerprint())) {
                throw new ReportRejected("payment-request-conflict", 409);
            }
            return result(request.requestId());
        }
        if (!reports.environment().equals(request.environment())) throw new ReportRejected("payment-environment", 409);
        if (!araka.configured()) throw new ReportRejected("payment-not-configured", 503);

        var now = Timestamp.from(clock.instant());
        jdbc.update("""
                insert into app.exception_payment_batch (id, actor_id, reason, environment, fingerprint)
                values (?, ?, ?, ?, ?)
                """, request.requestId(), actor, reason, request.environment(), fingerprint);
        UUID statement = null;
        for (var order : orders) {
            var candidate = reports.candidate(order.id());
            if (candidate == null || !Objects.equals(candidate.lineId(), order.lineId())
                    || !Objects.equals(candidate.previousTransferId(), order.previousTransferId())
                    || candidate.amount() != order.amount() || !Objects.equals(candidate.phone(), order.phone())) {
                throw new ReportRejected("exception-stale", 409);
            }
            if (!araka.permits(ArakaProperties.wallet(candidate.phone()), order.operator())) {
                throw new ReportRejected("payment-recipient-blocked", 400);
            }
            if (candidate.amount() > 999_999) throw new ReportRejected("payment-amount", 400);
            var prime = primes.findById(candidate.primeId()).orElseThrow(() -> new ReportRejected("exception-stale", 409));
            if (Prime.PROPOSED.equals(prime.getStatus())) approve(prime, actor, now);
            var line = candidate.lineId();
            if (line == null) {
                if (statement == null) statement = statement(actor, reason, now);
                line = UUID.randomUUID();
                jdbc.update("""
                        insert into app.payment_statement_line (id, statement_id, prime_id, threshold, amount, phone, released)
                        values (?, ?, ?, ?, ?, ?, false)
                        """, line, statement, prime.getId(), prime.getThreshold(), prime.getAmount(), prime.getPhone());
            }
            jdbc.update("""
                    insert into app.exception_payment_item (id, batch_id, line_id, provider)
                    values (?, ?, ?, ?)
                    """, UUID.randomUUID(), request.requestId(), line, order.operator());
        }
        audit.save(new AuditEntry(UUID.randomUUID(), actor, "exception-payment-authorization",
                "payment", request.requestId().toString(), "allowed", now.toInstant()));
        return result(request.requestId());
    }

    private void approve(Prime prime, UUID actor, Timestamp now) {
        var payout = numbers.findByDeclarantId(prime.getDeclarantId()).orElse(null);
        if (payout == null) {
            recordPayout.execute(prime.getDeclarantId(), actor);
            payout = numbers.findByDeclarantId(prime.getDeclarantId()).orElse(null);
        }
        if (payout == null || !PrimeContext.READY.equals(payout.getState()) || !Objects.equals(prime.getPhone(), payout.getPhone())) {
            throw new ReportRejected("prime-phone", 409);
        }
        beneficiaries.requireCapacity(prime);
        prime.approve(actor, now.toInstant());
        primes.saveAndFlush(prime);
        jdbc.update("""
                insert into app.prime_review (id, prime_id, result, actor_id, recorded_at)
                values (?, ?, 'approved', ?, ?)
                """, UUID.randomUUID(), prime.getId(), actor, now);
    }

    private UUID statement(UUID actor, String reason, Timestamp now) {
        var id = UUID.randomUUID();
        var today = LocalDate.ofInstant(now.toInstant(), ZoneId.of("Africa/Kinshasa"));
        var reference = "EX-" + today.getYear() + "-" + jdbc.queryForObject("select nextval('app.payment_statement_seq')", Long.class);
        jdbc.update("""
                insert into app.payment_statement (id, reference_code, status, prepared_by, prepared_at,
                    authorized_by, authorized_at, due_on, reason, exceptional)
                values (?, ?, 'authorized', ?, ?, ?, ?, ?, ?, true)
                """, id, reference, actor, now, actor, now, PaymentDeadline.dueOn(today), reason);
        return id;
    }

    private Batch result(UUID id) {
        return jdbc.queryForObject("""
                select count(*), sum(l.amount)
                from app.exception_payment_item i join app.payment_statement_line l on l.id = i.line_id
                where i.batch_id = ?
                """, (row, i) -> new Batch(id, row.getLong(1), row.getLong(2)), id);
    }

    private static String fingerprint(String environment, String reason, List<Order> orders) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            hash(digest, environment);
            hash(digest, reason);
            for (var order : orders) {
                hash(digest, order.id());
                hash(digest, order.lineId());
                hash(digest, order.previousTransferId());
                hash(digest, order.amount());
                hash(digest, order.phone());
                hash(digest, order.operator());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void hash(MessageDigest digest, Object value) {
        var bytes = Objects.toString(value, "").getBytes(StandardCharsets.UTF_8);
        digest.update((bytes.length + ":").getBytes(StandardCharsets.US_ASCII));
        digest.update(bytes);
    }
}
