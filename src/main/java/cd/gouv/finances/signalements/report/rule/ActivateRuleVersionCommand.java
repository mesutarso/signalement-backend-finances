package cd.gouv.finances.signalements.report.rule;

import cd.gouv.finances.signalements.report.ReportRejected;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ActivateRuleVersionCommand {

    private final JdbcTemplate jdbc;

    public ActivateRuleVersionCommand(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public void execute(UUID id) {
        var rows = jdbc.query("select active from app.rule_version where id = ?", (row, number) -> row.getBoolean(1), id);
        if (rows.isEmpty()) {
            throw new ReportRejected("rule-absent", 404);
        }
        if (Boolean.TRUE.equals(rows.getFirst())) {
            throw new ReportRejected("rule-state", 409);
        }
        jdbc.update("update app.rule_version set active = false where active = true and id <> ?", id);
        jdbc.update("update app.rule_version set active = true where id = ?", id);
    }
}
