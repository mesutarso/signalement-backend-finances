package cd.gouv.finances.signalements.report.message;

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
    private final ReadDocumentModelsQuery models;

    public RecordDocumentModelCommand(JdbcTemplate jdbc, Clock clock, ReadDocumentModelsQuery models) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.models = models;
    }

    @PreAuthorize("hasAuthority('template.write')")
    @Transactional
    public UUID execute(String kind, String body, UUID actorId) {
        var keptKind = kind == null ? "" : kind.trim();
        DeclarantMessageText.pattern(keptKind);
        var kept = DeclarantMessageText.validatePattern(body, models.referenceLength());
        jdbc.queryForObject("select pg_advisory_xact_lock(1107, ?)", Object.class, keptKind.hashCode());
        var id = UUID.randomUUID();
        var now = Timestamp.from(clock.instant());
        jdbc.update("update app.document_model set active = false where kind = ? and active = true", keptKind);
        jdbc.update("""
                insert into app.document_model (id, kind, body, active, actor_id, recorded_at)
                values (?, ?, ?, true, ?, ?)
                """, id, keptKind, kept, actorId, now);
        jdbc.update("""
                update app.declarant_message set body = replace(?, '%s', report_reference)
                where kind = ? and status in ('prepared','failed')
                """, kept, keptKind);
        return id;
    }
}
