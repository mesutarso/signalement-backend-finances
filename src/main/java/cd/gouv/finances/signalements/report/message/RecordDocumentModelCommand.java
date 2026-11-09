package cd.gouv.finances.signalements.report.message;

import cd.gouv.finances.signalements.report.ReportRejected;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RecordDocumentModelCommand {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public RecordDocumentModelCommand(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public UUID execute(String kind, String body, UUID actorId) {
        var keptKind = kind == null ? "" : kind.trim();
        DeclarantMessageText.pattern(keptKind);
        var kept = body == null ? "" : body.trim();
        var markers = kept.split("%s", -1).length - 1;
        if (kept.isEmpty() || kept.length() > 500 || markers != 1) {
            throw new ReportRejected("model-body", 400);
        }
        var id = UUID.randomUUID();
        var now = Timestamp.from(clock.instant());
        jdbc.update("update app.document_model set active = false where kind = ? and active = true", keptKind);
        jdbc.update("""
                insert into app.document_model (id, kind, body, active, actor_id, recorded_at)
                values (?, ?, ?, true, ?, ?)
                """, id, keptKind, kept, actorId, now);
        return id;
    }
}
