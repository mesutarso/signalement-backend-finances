package cd.gouv.finances.signalements.report.message;

import cd.gouv.finances.signalements.report.entity.DeclarantMessage;
import cd.gouv.finances.signalements.report.repository.DeclarantMessageRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MessageDrafts {

    private final JdbcTemplate jdbc;
    private final DeclarantMessageRepository declarantMessageRepository;
    private final Clock clock;

    public MessageDrafts(JdbcTemplate jdbc, DeclarantMessageRepository declarantMessageRepository, Clock clock) {
        this.jdbc = jdbc;
        this.declarantMessageRepository = declarantMessageRepository;
        this.clock = clock;
    }

    public String pattern(String kind) {
        var fallback = DeclarantMessageText.pattern(kind);
        var stored = jdbc.query(
                "select body from app.document_model where kind = ? and active = true order by recorded_at desc limit 1",
                (row, number) -> row.getString(1),
                kind);
        return stored.isEmpty() || stored.get(0) == null ? fallback : stored.get(0);
    }

    public String body(String kind, String reference) {
        return pattern(kind).formatted(reference.trim());
    }

    @Transactional
    public void prepare(UUID reportId, String kind, UUID actorId) {
        if (declarantMessageRepository.existsByReportIdAndKind(reportId, kind)) {
            return;
        }
        var references = jdbc.query(
                "select reference_code from app.report where id = ?",
                (row, number) -> row.getString(1),
                reportId);
        if (references.isEmpty() || references.get(0) == null || references.get(0).isBlank()) {
            return;
        }
        var message = new DeclarantMessage(
                UUID.randomUUID(),
                reportId,
                references.get(0),
                kind,
                body(kind, references.get(0)),
                actorId,
                clock.instant());
        declarantMessageRepository.save(message);
    }
}
