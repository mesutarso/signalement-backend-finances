package cd.gouv.finances.signalements.report.abuse;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.AbuseNote;
import cd.gouv.finances.signalements.report.repository.AbuseNoteRepository;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RecordAbuseNoteCommand {

    private static final Set<String> DECISIONS = Set.of(AbuseNote.NOTED, AbuseNote.DISMISSED);

    private final AbuseNoteRepository abuseNoteRepository;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public void execute(String fingerprint, String decision, String reason, UUID actorId) {
        if (!DECISIONS.contains(decision)) {
            throw new ReportRejected("abuse-decision", 400);
        }
        var shared = jdbc.queryForObject(
                """
                select count(distinct report_id)
                from app.report_photo
                where fingerprint = ?
                """,
                Integer.class,
                fingerprint);
        if (shared == null || shared < 2) {
            throw new ReportRejected("abuse-absent", 404);
        }
        var trimmed = reason.trim();
        var same = jdbc.queryForObject(
                """
                select count(*)
                from (
                    select decision, reason
                    from app.abuse_note
                    where fingerprint = ?
                    order by recorded_at desc
                    limit 1
                ) latest
                where decision = ? and reason = ?
                """,
                Integer.class,
                fingerprint,
                decision,
                trimmed);
        if (same != null && same > 0) {
            return;
        }
        abuseNoteRepository.save(new AbuseNote(
                UUID.randomUUID(), fingerprint, decision, trimmed, actorId, clock.instant()));
    }
}
