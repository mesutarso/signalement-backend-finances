package cd.gouv.finances.signalements.report.points;

import cd.gouv.finances.signalements.report.dto.PointMovementResponse;
import cd.gouv.finances.signalements.report.dto.PointPageResponse;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadPointsQuery {

    private static final int SIZE = 20;

    private final JdbcTemplate jdbc;

    public ReadPointsQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public PointPageResponse execute(UUID reportId, int page) {
        var requested = Math.max(page, 0);
        var where = reportId == null ? "" : "where movement.report_id = ? ";
        var filter = reportId == null ? new Object[] {} : new Object[] {reportId};
        var total = jdbc.queryForObject("select count(*) from app.point_movement movement " + where, Long.class, filter);
        var net = jdbc.queryForObject(
                "select coalesce(sum(movement.base_points + movement.bonus_points), 0) from app.point_movement movement " + where,
                Integer.class,
                filter);
        var args = new java.util.ArrayList<Object>();
        if (reportId != null) {
            args.add(reportId);
        }
        args.add(SIZE);
        args.add(requested * (long) SIZE);
        var items = jdbc.query("""
                select movement.id, movement.report_id, report.reference_code, movement.tax_id, movement.finding,
                       movement.base_points, movement.bonus_points, movement.kind, movement.dgi_reference, movement.reason,
                       movement.filed_on, movement.recorded_at
                from app.point_movement movement
                join app.report report on report.id = movement.report_id
                """ + where + """
                order by movement.recorded_at desc
                limit ? offset ?
                """, (row, number) -> new PointMovementResponse(
                row.getObject(1, UUID.class),
                row.getObject(2, UUID.class),
                row.getString(3),
                row.getString(4),
                row.getString(5),
                row.getInt(6),
                row.getInt(7),
                row.getString(8),
                row.getString(9),
                row.getString(10),
                row.getDate(11).toLocalDate(),
                row.getTimestamp(12).toInstant()), args.toArray());
        return new PointPageResponse(items, requested, SIZE, total == null ? 0 : total, net == null ? 0 : net);
    }
}
