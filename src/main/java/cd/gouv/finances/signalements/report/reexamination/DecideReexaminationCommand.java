package cd.gouv.finances.signalements.report.reexamination;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.Reexamination;
import cd.gouv.finances.signalements.report.repository.ReexaminationRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DecideReexaminationCommand {

    private final ReexaminationRepository reexaminationRepository;
    private final Clock clock;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public UUID execute(UUID id, String result, String comment, UUID actorId) {
        var kept = "maintained".equals(result) ? Reexamination.MAINTAINED : "modified".equals(result) ? Reexamination.MODIFIED : "";
        if (kept.isEmpty()) {
            throw new ReportRejected("reexamination-result", 400);
        }
        var note = comment == null ? "" : comment.trim();
        if (note.length() > 500) {
            throw new ReportRejected("reexamination-comment", 400);
        }
        var reexamination = reexaminationRepository.findById(id).orElseThrow(() -> new ReportRejected("reexamination-absent", 404));
        if (!Reexamination.RECEIVED.equals(reexamination.getStatus())) {
            throw new ReportRejected("reexamination-decided", 409);
        }
        if (Reexamination.MODIFIED.equals(kept)) {
            var corrected = jdbc.queryForObject("""
                    select exists(select 1 from app.qualification where report_id = ? and recorded_at > ?)
                    """, Boolean.class, reexamination.getReportId(), java.sql.Timestamp.from(reexamination.getRecordedAt()));
            if (!Boolean.TRUE.equals(corrected)) throw new ReportRejected("reexamination-correction", 409);
        }
        reexamination.decide(kept, note.isEmpty() ? null : note, actorId, clock.instant());
        return reexamination.getReportId();
    }
}
