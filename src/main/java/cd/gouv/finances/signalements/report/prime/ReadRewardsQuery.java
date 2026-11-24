package cd.gouv.finances.signalements.report.prime;

import cd.gouv.finances.signalements.araka.ArakaProperties;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.dto.RewardPageResponse;
import cd.gouv.finances.signalements.report.dto.PrimeReviewResponse;
import cd.gouv.finances.signalements.report.dto.RewardResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReadRewardsQuery {

    private static final int SIZE = 20;
    private static final String AUTHORIZED_REWARDS = """
            and exists (
                select 1 from app.payment_statement_line line
                join app.payment_statement statement on statement.id = line.statement_id
                where line.prime_id = prime.id and not line.released and statement.status = 'authorized'
            )
            """;

    private final JdbcTemplate jdbc;
    private final ArakaProperties araka;

    @PreAuthorize("hasAnyAuthority('reward.read', 'payment.execute')")
    @Transactional(readOnly = true)
    public RewardPageResponse execute(int page, UUID actor, boolean paymentOnly) {
        var requested = Math.max(page, 0);
        var filter = paymentOnly ? AUTHORIZED_REWARDS : "";
        var total = jdbc.queryForObject("select count(*) from app.prime where report_id is not null " + filter, Long.class);
        var environment = araka.sandbox() ? "sandbox" : "production";
        var rows = jdbc.query("""
                select id, amount, status, phone, recorded_at, report_id,
                       (select reference_code from app.report where id = app.prime.report_id)
                from app.prime where report_id is not null %s
                order by recorded_at desc, id
                limit ? offset ?
                """.formatted(filter), (row, number) -> map(row), SIZE, requested * (long) SIZE);
        return new RewardPageResponse(responses(rows, actor, environment), requested, SIZE, total == null ? 0 : total,
                environment, araka.configured());
    }

    @PreAuthorize("hasAuthority('reward.read')")
    @Transactional(readOnly = true)
    public RewardResponse executeOne(UUID primeId, UUID actor) {
        var rows = jdbc.query("""
                select id, amount, status, phone, recorded_at, report_id,
                       (select reference_code from app.report where id = app.prime.report_id)
                from app.prime
                where id = ? and report_id is not null
                """, (row, number) -> map(row), primeId);
        if (rows.isEmpty()) {
            throw new ReportRejected("prime-absent", 404);
        }
        return responses(rows, actor, araka.sandbox() ? "sandbox" : "production").getFirst();
    }

    private List<RewardResponse> responses(List<Row> rows, UUID actor, String environment) {
        var ids = rows.stream().map(Row::id).toList();
        var reviews = reviews(ids);
        var payments = payments(ids, actor, environment);
        return rows.stream()
                .map(row -> new RewardResponse(row.id(), row.amount(), row.status(), row.phone(), row.recordedAt(),
                        reviews.getOrDefault(row.id(), List.of()), row.reportId(), row.reportReference(), payments.get(row.id())))
                .toList();
    }

    private Map<UUID, RewardResponse.Payment> payments(List<UUID> ids, UUID actor, String environment) {
        if (ids.isEmpty()) return Map.of();
        var marks = String.join(",", ids.stream().map(id -> "?").toList());
        var arguments = new ArrayList<Object>();
        arguments.add(actor);
        arguments.add(environment);
        arguments.addAll(ids);
        return jdbc.query("""
                select p.id, l.id as line_id, s.id as statement_id, s.reference_code as statement_reference,
                       t.id as transfer_id, t.reference as transfer_reference,
                       case
                         when exists (
                             select 1 from app.payment_statement_line paid_line
                             join app.payment_attempt a on a.line_id = paid_line.id
                             where paid_line.prime_id = p.id and a.result = 'paid' and a.reference_code not like 'SIM-%%'
                         ) then 'paid'
                         when t.state in ('submitting', 'pending', 'unknown') then t.state
                         when t.state = 'paid' then 'test-paid'
                         when s.status is distinct from 'authorized' then 'awaiting-authorization'
                         when s.authorized_by = ? then 'another-payer'
                         when p.status <> 'approved' or r.validation_state <> 'validated'
                           or (n.state = 'ready' and n.phone = l.phone and p.phone = l.phone) is not true
                           or (select action from app.prime_suspension where declarant_id = p.declarant_id
                               order by recorded_at desc limit 1) = 'suspended' then 'blocked'
                         else coalesce(t.state, 'ready')
                       end as payment_state
                from app.prime p join app.report r on r.id = p.report_id
                left join app.payment_statement_line l on l.prime_id = p.id and not l.released
                left join app.payment_statement s on s.id = l.statement_id
                left join app.payout_number n on n.declarant_id = p.declarant_id
                left join lateral (
                    select id, state, reference from app.payment_transfer
                    where line_id = l.id and environment = ?
                    order by started_at desc, id desc limit 1
                ) t on true
                where p.id in (%s)
                """.formatted(marks), result -> {
            var payments = new LinkedHashMap<UUID, RewardResponse.Payment>();
            while (result.next()) {
                payments.put(result.getObject("id", UUID.class), new RewardResponse.Payment(
                        result.getObject("line_id", UUID.class), result.getObject("statement_id", UUID.class),
                        result.getString("statement_reference"), result.getString("payment_state"),
                        result.getObject("transfer_id", UUID.class), result.getString("transfer_reference")));
            }
            return payments;
        }, arguments.toArray());
    }

    private Map<UUID, List<PrimeReviewResponse>> reviews(List<UUID> ids) {
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }
        var marks = String.join(",", ids.stream().map(id -> "?").toList());
        var grouped = new LinkedHashMap<UUID, List<PrimeReviewResponse>>();
        jdbc.query("""
                select review.id, review.prime_id, review.result, review.reason, account.username, review.recorded_at
                from app.prime_review review
                left join app.user_account account on account.id = review.actor_id
                where review.prime_id in (%s)
                order by review.recorded_at desc
                """.formatted(marks), (row, number) -> {
            var primeId = row.getObject(2, UUID.class);
            grouped.computeIfAbsent(primeId, id -> new ArrayList<>()).add(new PrimeReviewResponse(
                    row.getObject(1, UUID.class),
                    row.getString(3),
                    row.getString(4),
                    row.getString(5),
                    row.getTimestamp(6).toInstant()));
            return null;
        }, ids.toArray());
        return grouped;
    }

    private static Row map(ResultSet row) throws SQLException {
        return new Row(row.getObject(1, UUID.class), row.getInt(2), row.getString(3), row.getString(4),
                row.getTimestamp(5).toInstant(), row.getObject(6, UUID.class), row.getString(7));
    }

    private record Row(UUID id, int amount, String status, String phone, Instant recordedAt, UUID reportId, String reportReference) {
    }
}
