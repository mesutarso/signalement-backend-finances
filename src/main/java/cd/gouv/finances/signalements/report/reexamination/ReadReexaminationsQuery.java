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

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public ReexaminationPageResponse execute(int page) {
        var requested = Math.max(page, 0);
        var total = jdbc.queryForObject("select count(*) from app.reexamination", Long.class);
        var items = jdbc.query("""
                select id, reference_code, report_reference, request_text, received_on, on_time, status, comment
                from app.reexamination
                order by recorded_at desc
                limit ? offset ?
                """, (row, number) -> new ReexaminationPageResponse.Item(
                row.getObject(1, UUID.class),
                row.getString(2),
                row.getString(3),
                row.getString(4),
                row.getDate(5).toLocalDate(),
                row.getBoolean(6),
                row.getString(7),
                row.getString(8)), SIZE, requested * (long) SIZE);
        return new ReexaminationPageResponse(items, requested, SIZE, total == null ? 0 : total);
    }
}
