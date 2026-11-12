package cd.gouv.finances.signalements.report.validation;

import cd.gouv.finances.signalements.report.ReportRejected;
import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RewardSettings {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public record Setting(UUID id, int amount, boolean active, String actorName, Instant recordedAt) { }

    public Setting active() {
        return jdbc.queryForObject("""
                select s.*, u.username from app.reward_setting s
                left join app.user_account u on u.id = s.actor_id where active
                """, (r, n) -> new Setting(r.getObject("id", UUID.class), r.getInt("amount"), true,
                r.getString("username"), r.getTimestamp("recorded_at").toInstant()));
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    public Setting read() { return active(); }

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public void change(int amount, UUID expectedId, UUID actorId) {
        if (amount <= 0) throw new ReportRejected("reward-amount", 400);
        jdbc.queryForObject("select pg_advisory_xact_lock(783962145)", Object.class);
        var current = active();
        if (!current.id().equals(expectedId)) throw new ReportRejected("stale", 409);
        if (current.amount() == amount) return;
        jdbc.update("update app.reward_setting set active = false where active");
        jdbc.update("insert into app.reward_setting values (?, ?, true, ?, ?)",
                UUID.randomUUID(), amount, actorId, Timestamp.from(clock.instant()));
    }
}
