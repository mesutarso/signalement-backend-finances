package cd.gouv.finances.signalements.report.rule;

import cd.gouv.finances.signalements.report.ReportRejected;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Retains the beneficiary ceiling independently of the retired point scale. */
@Component
@RequiredArgsConstructor
public class ActiveRule {
    private final JdbcTemplate jdbc;

    public record Snapshot(int ceiling) { }

    public Snapshot requireActive() {
        var rows = jdbc.query("select ceiling from app.rule_version where active", (row, number) -> new Snapshot(row.getInt(1)));
        if (rows.size() != 1) throw new ReportRejected("rule-absent", 409);
        return rows.getFirst();
    }
}
