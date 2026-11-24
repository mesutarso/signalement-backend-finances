package cd.gouv.finances.signalements.report.reexamination;

import cd.gouv.finances.signalements.report.dto.ReexaminationPageResponse;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadReexaminationsQuery {

    private static final int SIZE = 20;

    private final JdbcTemplate jdbc;

    public ReadReexaminationsQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PreAuthorize("hasAuthority('reexam.read')")
    @Transactional(readOnly = true)
    public ReexaminationPageResponse execute(int page) {
        return execute(page, null);
    }

    @PreAuthorize("hasAuthority('reexam.read')")
    @Transactional(readOnly = true)
    public ReexaminationPageResponse execute(int page, UUID reportId) {
        var requested = Math.max(page, 0);
        var filter = reportId == null ? "" : " where reexamination.report_id = ?";
        var args = new java.util.ArrayList<>();
        if (reportId != null) args.add(reportId);
        var total = jdbc.queryForObject("select count(*) from app.reexamination reexamination" + filter, Long.class, args.toArray());
        args.add(SIZE);
        args.add(requested * (long) SIZE);
        var items = jdbc.query("""
                select id, reference_code, report_reference, request_text, received_on, on_time, status, comment
                from app.reexamination
                %s
                order by recorded_at desc, id desc
                limit ? offset ?
                """.formatted(filter), (row, number) -> new ReexaminationPageResponse.Item(
                row.getObject(1, UUID.class),
                row.getString(2),
                row.getString(3),
                row.getString(4),
                row.getDate(5).toLocalDate(),
                row.getBoolean(6),
                row.getString(7),
                row.getString(8)), args.toArray());
        return new ReexaminationPageResponse(items, requested, SIZE, total == null ? 0 : total);
    }
}
