package cd.gouv.finances.signalements.report.prime;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.dto.PrimeAccountResponse;
import cd.gouv.finances.signalements.report.dto.PrimePageResponse;
import cd.gouv.finances.signalements.report.dto.PrimeReviewResponse;
import cd.gouv.finances.signalements.report.dto.PrimeTierResponse;
import cd.gouv.finances.signalements.report.entity.Prime;
import cd.gouv.finances.signalements.report.rule.ActiveRule;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadPrimesQuery {

    private static final int SIZE = 20;

    private final JdbcTemplate jdbc;
    private final PrimeContext primeContext;
    private final ActiveRule activeRule;

    public ReadPrimesQuery(JdbcTemplate jdbc, PrimeContext primeContext, ActiveRule activeRule) {
        this.jdbc = jdbc;
        this.primeContext = primeContext;
        this.activeRule = activeRule;
    }

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public PrimePageResponse execute(int page) {
        var requested = Math.max(page, 0);
        var total = jdbc.queryForObject("select count(*) from app.prime", Long.class);
        var rows = jdbc.query("""
                select id, threshold, amount, status, phone, recorded_at
                from app.prime
                order by recorded_at desc
                limit ? offset ?
                """, (row, number) -> row(row.getObject(1, UUID.class), row.getInt(2), row.getInt(3), row.getString(4), row.getString(5), row.getTimestamp(6)), SIZE, requested * (long) SIZE);
        return new PrimePageResponse(withReviews(rows), requested, SIZE, total == null ? 0 : total);
    }

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public PrimeAccountResponse executeForReport(UUID reportId) {
        var declarantId = primeContext.declarantId(reportId);
        if (declarantId.isBlank()) {
            return empty();
        }
        var rule = activeRule.requireForReport(reportId);
        var net = primeContext.netPoints(declarantId, rule.code());
        var phone = recordedPhone(declarantId);
        var rows = jdbc.query("""
                select id, threshold, amount, status, phone, recorded_at
                from app.prime
                where declarant_id = ? and rule_version = ?
                order by threshold
                """, (row, number) -> row(row.getObject(1, UUID.class), row.getInt(2), row.getInt(3), row.getString(4), row.getString(5), row.getTimestamp(6)), declarantId, rule.code());
        var tiers = withReviews(rows);
        var granted = tiers.stream()
                .filter(tier -> Prime.PROPOSED.equals(tier.status()) || Prime.APPROVED.equals(tier.status()))
                .mapToInt(PrimeTierResponse::amount)
                .sum();
        var visiblePhone = PrimeContext.READY.equals(phone.state()) ? phone.number() : null;
        return new PrimeAccountResponse(net, PrimeScale.surplus(net, rule.ceiling(), rule.tiers()), granted, rule.ceiling(), phone.state(), visiblePhone, tiers);
    }

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public PrimeTierResponse executeOne(UUID primeId) {
        var rows = jdbc.query("""
                select id, threshold, amount, status, phone, recorded_at
                from app.prime
                where id = ?
                """, (row, number) -> row(row.getObject(1, UUID.class), row.getInt(2), row.getInt(3), row.getString(4), row.getString(5), row.getTimestamp(6)), primeId);
        if (rows.isEmpty()) {
            throw new ReportRejected("prime-absent", 404);
        }
        return withReviews(rows).getFirst();
    }

    private List<PrimeTierResponse> withReviews(List<Row> rows) {
        var reviews = reviews(rows.stream().map(Row::id).toList());
        return rows.stream()
                .map(row -> new PrimeTierResponse(row.id(), row.threshold(), row.amount(), row.status(), row.phone(), row.recordedAt(), reviews.getOrDefault(row.id(), List.of())))
                .toList();
    }

    private java.util.Map<UUID, List<PrimeReviewResponse>> reviews(List<UUID> ids) {
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

    private static PrimeAccountResponse empty() {
        return new PrimeAccountResponse(0, 0, 0, PrimeScale.CEILING, PrimeContext.UNRECORDED, null, List.of());
    }

    private PrimeContext.Phone recordedPhone(String declarantId) {
        var rows = jdbc.query("""
                select phone, state
                from app.payout_number
                where declarant_id = ?
                """, (row, number) -> new PrimeContext.Phone(row.getString(1), row.getString(2)), declarantId);
        if (rows.isEmpty()) {
            return new PrimeContext.Phone(null, PrimeContext.UNRECORDED);
        }
        return rows.getFirst();
    }

    private static Row row(UUID id, int threshold, int amount, String status, String phone, Timestamp recordedAt) {
        return new Row(id, threshold, amount, status, phone, recordedAt.toInstant());
    }

    private record Row(UUID id, int threshold, int amount, String status, String phone, Instant recordedAt) {
    }
}
