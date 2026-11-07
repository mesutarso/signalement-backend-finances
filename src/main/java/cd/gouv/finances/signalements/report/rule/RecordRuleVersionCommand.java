package cd.gouv.finances.signalements.report.rule;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.dto.RuleVersionBody;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RecordRuleVersionCommand {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public RecordRuleVersionCommand(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public UUID execute(RuleVersionBody body, UUID actorId) {
        var draft = RuleDraft.require(
                body.code(),
                body.fakeInvoice(),
                body.missingTransmission(),
                body.understated(),
                body.nonNormalized(),
                body.refusal(),
                body.bonus(),
                body.ceiling(),
                body.dailyCap(),
                body.spacingDays(),
                body.tiers() == null ? java.util.List.of() : body.tiers().stream().map(tier -> new RuleDraft.Tier(tier.threshold(), tier.amount())).toList());
        var existing = jdbc.queryForObject("select count(*) from app.rule_version where code = ?", Long.class, draft.code());
        if (existing != null && existing > 0) {
            throw new ReportRejected("rule-open", 409);
        }
        var id = UUID.randomUUID();
        var scale = draft.scale();
        jdbc.update("""
                insert into app.rule_version (
                    id, code, fake_invoice, missing_transmission, understated, non_normalized, refusal,
                    bonus, ceiling, daily_cap, spacing_days, active, actor_id, recorded_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, false, ?, ?)
                """, id, draft.code(), scale.fakeInvoice(), scale.missingTransmission(), scale.understated(),
                scale.nonNormalized(), scale.refusal(), scale.bonus(), draft.ceiling(), scale.dailyCap(),
                scale.spacingDays(), actorId, Timestamp.from(clock.instant()));
        var position = 1;
        for (var tier : draft.tiers()) {
            jdbc.update("""
                    insert into app.rule_tier (id, rule_id, threshold, amount, position)
                    values (?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), id, tier.threshold(), tier.amount(), position);
            position++;
        }
        return id;
    }
}
