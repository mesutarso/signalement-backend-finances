package cd.gouv.finances.signalements.audit.query;

import cd.gouv.finances.signalements.audit.dto.AuditEntryResponse;
import cd.gouv.finances.signalements.audit.dto.AuditPageResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadAuditQuery {

    private static final ZoneId KINSHASA = ZoneId.of("Africa/Kinshasa");
    private static final int SIZE = 20;

    private final JdbcTemplate jdbc;

    public ReadAuditQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public AuditPageResponse execute(String objectType, String objectId, LocalDate from, LocalDate to, int page) {
        var start = (from == null ? LocalDate.now(KINSHASA).minusDays(30) : from).atStartOfDay(KINSHASA).toInstant();
        var end = (to == null ? LocalDate.now(KINSHASA).plusDays(1) : to.plusDays(1)).atStartOfDay(KINSHASA).toInstant();
        var requested = Math.max(page, 0);
        var filters = new ArrayList<Object>();
        filters.add(Timestamp.from(start));
        filters.add(Timestamp.from(end));
        var where = new StringBuilder(" where recorded_at >= ? and recorded_at < ? and action not in ('points-credit', 'points-reversal', 'prime-proposal', 'prime-withdrawal', 'rule-version', 'rule-activation', 'payment-paid', 'payment-failed', 'payment-unknown') and object_type <> 'prime'");
        if (present(objectType)) {
            where.append(" and object_type = ?");
            filters.add(objectType.trim());
        }
        if (present(objectId)) {
            where.append(" and object_id = ?");
            filters.add(objectId.trim());
        }
        var total = jdbc.queryForObject("select count(*) from app.audit_entry" + where, Long.class, filters.toArray());
        var pageFilters = new ArrayList<>(filters);
        pageFilters.add(SIZE);
        pageFilters.add(requested * SIZE);
        var entries = jdbc.query(
                """
                select id, actor_id, action, object_type, object_id, result, recorded_at
                from app.audit_entry
                """ + where + " order by recorded_at desc limit ? offset ?",
                (row, number) -> new Stored(
                        row.getObject(1, UUID.class),
                        row.getObject(2, UUID.class),
                        row.getString(3),
                        row.getString(4),
                        row.getString(5),
                        row.getString(6),
                        row.getTimestamp(7).toInstant()),
                pageFilters.toArray());
        var names = names(entries.stream().map(Stored::actorId).filter(id -> id != null).distinct().toList());
        return new AuditPageResponse(
                entries.stream().map(entry -> new AuditEntryResponse(
                        entry.id(),
                        entry.actorId() == null ? null : names.get(entry.actorId()),
                        entry.action(),
                        entry.objectType(),
                        entry.objectId(),
                        entry.result(),
                        entry.recordedAt())).toList(),
                requested,
                SIZE,
                total == null ? 0 : total);
    }

    private java.util.Map<UUID, String> names(java.util.List<UUID> ids) {
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }
        var marks = String.join(",", ids.stream().map(id -> "?").toList());
        var found = new java.util.HashMap<UUID, String>();
        jdbc.query(
                "select id, username from app.user_account where id in (%s)".formatted(marks),
                (row, number) -> found.put(row.getObject(1, UUID.class), row.getString(2)),
                ids.toArray());
        return found;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private record Stored(
            UUID id,
            UUID actorId,
            String action,
            String objectType,
            String objectId,
            String result,
            Instant recordedAt) {
    }
}
