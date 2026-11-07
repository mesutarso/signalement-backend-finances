package cd.gouv.finances.signalements.report.supervision;

import cd.gouv.finances.signalements.report.dto.SupervisionItemResponse;
import cd.gouv.finances.signalements.report.dto.SupervisionPageResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadSupervisionQuery {

    private static final int SIZE = 20;
    private static final Set<String> FILTERS = Set.of("open", "upheld", "returned", "all");

    private final JdbcTemplate jdbc;

    public ReadSupervisionQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public SupervisionPageResponse execute(String status, int page) {
        var filter = FILTERS.contains(status) ? status : "open";
        var requested = Math.max(page, 0);
        var where = switch (filter) {
            case "upheld", "returned" -> "where review_result = ?";
            case "all" -> "";
            default -> "where review_result is null";
        };
        var args = new ArrayList<Object>();
        if ("upheld".equals(filter) || "returned".equals(filter)) {
            args.add(filter);
        }
        var total = jdbc.queryForObject(countSql(where), Long.class, args.toArray());
        args.add(SIZE);
        args.add(requested * (long) SIZE);
        var rows = jdbc.query(pageSql(where), (row, number) -> new Row(
                row.getObject(1, UUID.class),
                row.getString(2),
                row.getTimestamp(3).toLocalDateTime(),
                row.getString(4),
                row.getObject(5, UUID.class),
                row.getString(6),
                row.getString(7),
                row.getString(8),
                row.getString(9),
                row.getObject(10, UUID.class),
                row.getTimestamp(11).toInstant(),
                row.getString(12),
                row.getString(13),
                row.getObject(14, UUID.class),
                row.getTimestamp(15) == null ? null : row.getTimestamp(15).toInstant()), args.toArray());
        var names = names(rows);
        var items = rows.stream().map(row -> new SupervisionItemResponse(
                row.reportId(),
                row.identifier(),
                row.reportedAt(),
                row.title(),
                row.qualificationId(),
                row.decision(),
                row.finding(),
                row.proof(),
                row.justification(),
                name(names, row.qualifierId()),
                row.qualifiedAt(),
                row.reviewResult(),
                row.reviewReason(),
                name(names, row.reviewerId()),
                row.reviewedAt())).toList();
        return new SupervisionPageResponse(items, requested, SIZE, total == null ? 0 : total);
    }

    private java.util.Map<UUID, String> names(java.util.List<Row> rows) {
        var ids = new ArrayList<UUID>();
        for (var row : rows) {
            if (row.qualifierId() != null) {
                ids.add(row.qualifierId());
            }
            if (row.reviewerId() != null) {
                ids.add(row.reviewerId());
            }
        }
        var distinct = ids.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return java.util.Map.of();
        }
        var marks = String.join(",", distinct.stream().map(id -> "?").toList());
        var found = new HashMap<UUID, String>();
        jdbc.query(
                "select id, username from app.user_account where id in (%s)".formatted(marks),
                (row, number) -> found.put(row.getObject(1, UUID.class), row.getString(2)),
                distinct.toArray());
        return found;
    }

    private static String name(java.util.Map<UUID, String> names, UUID id) {
        return id == null ? null : names.get(id);
    }

    private static String countSql(String where) {
        return "select count(*) from (" + latestSql() + ") review " + where;
    }

    private static String pageSql(String where) {
        return "select report_id, identifier, reported_at, title, qualification_id, decision, finding, proof, justification, qualifier_id, qualified_at, review_result, review_reason, reviewer_id, reviewed_at from ("
                + latestSql()
                + ") review "
                + where
                + " order by qualified_at desc limit ? offset ?";
    }

    private static String latestSql() {
        return """
                select qualification.report_id,
                       report.external_id as identifier,
                       report.reported_at,
                       report.title,
                       qualification.id as qualification_id,
                       qualification.decision,
                       qualification.finding,
                       qualification.proof,
                       qualification.justification,
                       qualification.actor_id as qualifier_id,
                       qualification.recorded_at as qualified_at,
                       review.result as review_result,
                       review.reason as review_reason,
                       review.actor_id as reviewer_id,
                       review.recorded_at as reviewed_at
                from (
                    select distinct on (report_id) *
                    from app.qualification
                    order by report_id, recorded_at desc
                ) qualification
                join app.report report on report.id = qualification.report_id
                left join (
                    select distinct on (qualification_id) *
                    from app.supervision_review
                    order by qualification_id, recorded_at desc
                ) review on review.qualification_id = qualification.id
                """;
    }

    private record Row(
            UUID reportId,
            String identifier,
            java.time.LocalDateTime reportedAt,
            String title,
            UUID qualificationId,
            String decision,
            String finding,
            String proof,
            String justification,
            UUID qualifierId,
            Instant qualifiedAt,
            String reviewResult,
            String reviewReason,
            UUID reviewerId,
            Instant reviewedAt) {
    }
}
