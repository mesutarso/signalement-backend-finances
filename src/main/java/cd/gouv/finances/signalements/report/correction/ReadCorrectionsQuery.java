package cd.gouv.finances.signalements.report.correction;

import cd.gouv.finances.signalements.report.dto.CorrectionPageResponse;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadCorrectionsQuery {

    private static final int SIZE = 20;

    private final JdbcTemplate jdbc;

    public ReadCorrectionsQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public CorrectionPageResponse execute(int page) {
        var requested = Math.max(page, 0);
        var total = jdbc.queryForObject("select count(*) from app.decision_correction", Long.class);
        var items = jdbc.query("""
                select correction.id, report.reference_code, correction.reason, correction.recorded_at
                from app.decision_correction correction
                join app.report report on report.id = correction.report_id
                order by correction.recorded_at desc
                limit ? offset ?
                """, (row, number) -> new CorrectionPageResponse.Item(
                row.getObject(1, UUID.class),
                row.getString(2),
                row.getString(3),
                row.getTimestamp(4).toInstant()), SIZE, requested * (long) SIZE);
        return new CorrectionPageResponse(items, requested, SIZE, total == null ? 0 : total);
    }
}
