package cd.gouv.finances.signalements.report.rule;

import cd.gouv.finances.signalements.report.dto.RulePageResponse;
import cd.gouv.finances.signalements.report.dto.RuleVersionBody;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadRuleVersionsQuery {

    private final JdbcTemplate jdbc;

    public ReadRuleVersionsQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public RulePageResponse execute() {
        var versions = jdbc.query("""
                select id, code, fake_invoice, missing_transmission, understated, non_normalized, refusal,
                       bonus, ceiling, daily_cap, spacing_days, active, recorded_at
                from app.rule_version
                order by recorded_at desc, code
                """, (row, number) -> new RulePageResponse.Version(
                row.getObject(1, UUID.class),
                row.getString(2),
                row.getInt(3),
                row.getInt(4),
                row.getInt(5),
                row.getInt(6),
                row.getInt(7),
                row.getInt(8),
                row.getInt(9),
                row.getInt(10),
                row.getInt(11),
                tiers(row.getObject(1, UUID.class)),
                row.getBoolean(12),
                row.getTimestamp(13).toInstant()));
        return new RulePageResponse(versions);
    }

    private java.util.List<RuleVersionBody.Tier> tiers(UUID ruleId) {
        return jdbc.query("""
                select threshold, amount from app.rule_tier
                where rule_id = ?
                order by position
                """, (row, number) -> new RuleVersionBody.Tier(row.getInt(1), row.getInt(2)), ruleId);
    }
}
