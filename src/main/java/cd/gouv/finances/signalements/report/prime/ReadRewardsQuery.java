package cd.gouv.finances.signalements.report.prime;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.dto.RewardPageResponse;
import cd.gouv.finances.signalements.report.dto.PrimeReviewResponse;
import cd.gouv.finances.signalements.report.dto.RewardResponse;
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
public class ReadRewardsQuery {

    private static final int SIZE = 20;

    private final JdbcTemplate jdbc;
    public ReadRewardsQuery(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public RewardPageResponse execute(int page) {
        var requested = Math.max(page, 0);
        var total = jdbc.queryForObject("select count(*) from app.prime where report_id is not null", Long.class);
        var rows = jdbc.query("""
                select id, threshold, amount, status, phone, recorded_at, report_id,
                       (select reference_code from app.report where id = app.prime.report_id)
                from app.prime where report_id is not null
                order by recorded_at desc
                limit ? offset ?
                """, (row, number) -> row(row.getObject(1, UUID.class), row.getInt(2), row.getInt(3), row.getString(4), row.getString(5), row.getTimestamp(6), row.getObject(7, UUID.class), row.getString(8)), SIZE, requested * (long) SIZE);
        return new RewardPageResponse(withReviews(rows), requested, SIZE, total == null ? 0 : total);
    }

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public RewardResponse executeOne(UUID primeId) {
        var rows = jdbc.query("""
                select id, threshold, amount, status, phone, recorded_at, report_id,
                       (select reference_code from app.report where id = app.prime.report_id)
                from app.prime
                where id = ? and report_id is not null
                """, (row, number) -> row(row.getObject(1, UUID.class), row.getInt(2), row.getInt(3), row.getString(4), row.getString(5), row.getTimestamp(6), row.getObject(7, UUID.class), row.getString(8)), primeId);
        if (rows.isEmpty()) {
            throw new ReportRejected("prime-absent", 404);
        }
        return withReviews(rows).getFirst();
    }

    private List<RewardResponse> withReviews(List<Row> rows) {
        var reviews = reviews(rows.stream().map(Row::id).toList());
        return rows.stream()
                .map(row -> new RewardResponse(row.id(), row.amount(), row.status(), row.phone(), row.recordedAt(), reviews.getOrDefault(row.id(), List.of()), row.reportId(), row.reportReference()))
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

    private static Row row(UUID id, int threshold, int amount, String status, String phone, Timestamp recordedAt, UUID reportId, String reportReference) {
        return new Row(id, threshold, amount, status, phone, recordedAt.toInstant(), reportId, reportReference);
    }

    private record Row(UUID id, int threshold, int amount, String status, String phone, Instant recordedAt, UUID reportId, String reportReference) {
    }
}
