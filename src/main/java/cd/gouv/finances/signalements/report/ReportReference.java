package cd.gouv.finances.signalements.report;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReportReference {

    private final JdbcTemplate jdbc;

    public ReportReference(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Match require(String reference) {
        var kept = reference == null ? "" : reference.trim();
        if (kept.isEmpty() || kept.length() > 80) {
            throw new ReportRejected("report-absent", 404);
        }
        var rows = jdbc.query(
                "select id, reference_code from app.report where reference_code = ?",
                (row, number) -> new Match(row.getObject(1, UUID.class), row.getString(2)),
                kept);
        if (rows.isEmpty()) {
            throw new ReportRejected("report-absent", 404);
        }
        if (rows.size() > 1) {
            throw new ReportRejected("ambiguous-report", 409);
        }
        return rows.getFirst();
    }

    public record Match(UUID id, String reference) {
    }
}
