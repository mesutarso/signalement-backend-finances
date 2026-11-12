package cd.gouv.finances.signalements.report.payment;

import cd.gouv.finances.signalements.araka.ArakaProperties;
import cd.gouv.finances.signalements.araka.ArakaResult;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.prime.BeneficiaryRegistry;
import cd.gouv.finances.signalements.report.prime.PrimeGuard;
import cd.gouv.finances.signalements.report.validation.RewardPaymentGuard;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PaymentTransfers {
    private final JdbcTemplate jdbc;
    private final BeneficiaryRegistry beneficiaries;
    private final RewardPaymentGuard rewards;
    private final PrimeGuard primeGuard;
    private final ArakaProperties properties;
    private final Clock clock;

    public record Transfer(UUID id, UUID lineId, String environment, String reference, String provider, String wallet,
            BigDecimal amount, String state, String code, String transactionId, UUID actorId, Instant startedAt, Instant updatedAt) {
        @Override public String toString() { return "Transfer[id=" + id + ", state=" + state + "]"; }
    }
    public record Claim(Transfer transfer, boolean submit) { }
    public record Check(Transfer transfer, UUID lease) { }

    @Transactional
    public Claim reserveLine(UUID statement, UUID line, String operator, UUID request, UUID actor) {
        beneficiaries.lock();
        var existing = byRequest(request);
        if (existing != null) {
            if (!actor.equals(existing.actorId()) || !line.equals(existing.lineId()) || !java.util.Objects.equals(ArakaProperties.provider(operator), existing.provider()))
                throw new ReportRejected("payment-request-conflict", 409);
            requireEnvironment(existing);
            return new Claim(existing, false);
        }
        var candidates = jdbc.query("""
                select l.prime_id, l.amount, l.phone, l.released, s.status, s.authorized_by, p.declarant_id,
                       p.phone = l.phone and n.phone = l.phone and n.state = 'ready' as same_beneficiary
                from app.payment_statement_line l join app.payment_statement s on s.id = l.statement_id
                join app.prime p on p.id = l.prime_id left join app.payout_number n on n.declarant_id = p.declarant_id
                where l.id = ? and s.id = ?
                """, (r,n) -> new Line(r.getObject(1, UUID.class), r.getBigDecimal(2), r.getString(3), r.getBoolean(4),
                        r.getString(5), r.getObject(6, UUID.class), r.getString(7), r.getBoolean(8)), line, statement);
        if (candidates.isEmpty() || candidates.getFirst().released()) throw new ReportRejected("payment-line", 404);
        var source = candidates.getFirst();
        if (!"authorized".equals(source.status())) throw new ReportRejected("payment-state", 409);
        if (actor.equals(source.authorizedBy())) throw new ReportRejected("payment-separation", 409);
        rewards.requirePrime(source.prime());
        if (!source.sameBeneficiary() || primeGuard.suspended(source.declarant())) throw new ReportRejected("payment-beneficiary", 409);
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from app.payment_attempt where line_id = ? and result = 'paid' and reference_code not like 'SIM-%')
                """, Boolean.class, line))) throw new ReportRejected("payment-paid", 409);
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from app.payment_transfer where line_id = ? and environment = ? and state <> 'failed')
                """, Boolean.class, line, environment()))) throw new ReportRejected("payment-transfer-busy", 409);
        return insert(line, source.amount(), source.phone(), operator, request, actor);
    }

    @Transactional
    public Claim reserveTest(String phone, String operator, UUID request, UUID actor) {
        if (!properties.sandbox()) throw new ReportRejected("payment-test-disabled", 409);
        beneficiaries.lock();
        var existing = byRequest(request);
        if (existing != null) {
            if (!actor.equals(existing.actorId()) || existing.lineId() != null || !java.util.Objects.equals(existing.wallet(), ArakaProperties.wallet(phone))
                    || !java.util.Objects.equals(existing.provider(), ArakaProperties.provider(operator))) throw new ReportRejected("payment-request-conflict", 409);
            requireEnvironment(existing);
            return new Claim(existing, false);
        }
        return insert(null, new BigDecimal("5000.00"), phone, operator, request, actor);
    }

    private Claim insert(UUID line, BigDecimal amount, String phone, String operator, UUID request, UUID actor) {
        if (!properties.configured()) throw new ReportRejected("payment-not-configured", 503);
        var provider = ArakaProperties.provider(operator);
        var wallet = ArakaProperties.wallet(phone);
        if (provider == null) throw new ReportRejected("payment-operator", 400);
        if (!properties.permits(wallet, provider)) throw new ReportRejected("payment-recipient-blocked", 400);
        if (amount.signum() <= 0 || amount.compareTo(new BigDecimal("999999.99")) > 0) throw new ReportRejected("payment-amount", 400);
        var id = UUID.randomUUID();
        var reference = "FC" + UUID.randomUUID().toString().replace("-", "").substring(0,18);
        jdbc.update("""
                insert into app.payment_transfer (id, request_id, line_id, environment, reference, provider, wallet, amount, state, code, actor_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, 'submitting', 'submitting', ?)
                """, id, request, line, environment(), reference, provider, wallet, amount, actor);
        return new Claim(read(id), true);
    }

    @Transactional
    public Transfer submitted(UUID id, ArakaResult result) {
        beneficiaries.lock();
        var current = read(id);
        if ("submitting".equals(current.state())) apply(current, result);
        return read(id);
    }

    @Transactional
    public Check claimCheck(UUID id) {
        beneficiaries.lock();
        var current = read(id);
        requireEnvironment(current);
        if (List.of("paid", "failed").contains(current.state())) return new Check(current, null);
        var lease = UUID.randomUUID();
        var claimed = jdbc.update("""
                update app.payment_transfer set lease_id = ?, lease_until = current_timestamp + interval '60 seconds',
                    check_count = check_count + 1, next_check_at = current_timestamp + interval '5 minutes'
                where id = ? and (lease_until is null or lease_until < current_timestamp)
                  and (state <> 'submitting' or started_at < current_timestamp - interval '90 seconds')
                """, lease, id);
        return new Check(current, claimed == 1 ? lease : null);
    }

    @Transactional
    public Transfer checked(Check check, ArakaResult result) {
        beneficiaries.lock();
        var owners = jdbc.queryForList("select lease_id from app.payment_transfer where id = ?", UUID.class, check.transfer().id());
        var current = read(check.transfer().id());
        if (check.lease() != null && check.lease().equals(owners.getFirst()) && !List.of("paid", "failed").contains(current.state())) {
            apply(current, result);
            jdbc.update("update app.payment_transfer set lease_id = null, lease_until = null where id = ?", current.id());
        }
        return read(current.id());
    }

    private void apply(Transfer current, ArakaResult result) {
        jdbc.update("""
                update app.payment_transfer set state = ?, code = ?, transaction_id = coalesce(?, transaction_id),
                    updated_at = current_timestamp, next_check_at = current_timestamp + interval '30 seconds' where id = ?
                """, result.state(), result.code(), result.transactionId(), current.id());
        if ("production".equals(current.environment()) && "paid".equals(result.state())) {
            jdbc.update("""
                    insert into app.payment_attempt (id, statement_id, line_id, operator, result, reference_code, actor_id, recorded_at)
                    select ?, l.statement_id, l.id, ?, 'paid', ?, ?, current_timestamp from app.payment_statement_line l where l.id = ?
                    on conflict (id) do nothing
                    """, current.id(), "MPESA".equals(current.provider()) ? "m-pesa" : current.provider().toLowerCase(java.util.Locale.ROOT),
                    current.reference(), current.actorId(), current.lineId());
        }
    }

    public Transfer read(UUID id) {
        var rows = jdbc.query("select * from app.payment_transfer where id = ?", PaymentTransfers::map, id);
        if (rows.isEmpty()) throw new ReportRejected("payment-transfer-absent", 404);
        return rows.getFirst();
    }

    public List<Transfer> forStatement(UUID statement) {
        return jdbc.query("select t.* from app.payment_transfer t join app.payment_statement_line l on l.id = t.line_id where l.statement_id = ? order by t.started_at desc, t.id", PaymentTransfers::map, statement);
    }
    public List<Transfer> recent() { return jdbc.query("select * from app.payment_transfer order by started_at desc, id limit 30", PaymentTransfers::map); }
    public List<UUID> due() {
        return jdbc.queryForList("""
                select id from app.payment_transfer where environment = ? and state in ('pending', 'unknown', 'submitting')
                  and next_check_at <= current_timestamp and check_count < 48
                  and (lease_until is null or lease_until < current_timestamp)
                order by next_check_at limit 4
                """, UUID.class, environment());
    }

    private Transfer byRequest(UUID request) {
        var rows = jdbc.query("select * from app.payment_transfer where request_id = ?", PaymentTransfers::map, request);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private String environment() { return properties.sandbox() ? "sandbox" : "production"; }
    private void requireEnvironment(Transfer transfer) {
        if (!environment().equals(transfer.environment())) throw new ReportRejected("payment-environment", 409);
    }
    private static Transfer map(ResultSet r, int n) throws SQLException {
        var state = r.getString("state");
        return new Transfer(r.getObject("id", UUID.class), r.getObject("line_id", UUID.class), r.getString("environment"),
                r.getString("reference"), r.getString("provider"), r.getString("wallet"), r.getBigDecimal("amount"), state,
                r.getString("code"), r.getString("transaction_id"), r.getObject("actor_id", UUID.class),
                r.getTimestamp("started_at").toInstant(), r.getTimestamp("updated_at").toInstant());
    }
    private record Line(UUID prime, BigDecimal amount, String phone, boolean released, String status, UUID authorizedBy, String declarant, boolean sameBeneficiary) { }
}
