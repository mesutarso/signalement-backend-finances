package cd.gouv.finances.signalements.report.reexamination;

import cd.gouv.finances.signalements.report.ReportReference;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.Reexamination;
import cd.gouv.finances.signalements.report.repository.ReexaminationRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RecordReexaminationCommand {

    private static final ZoneId KINSHASA = ZoneId.of("Africa/Kinshasa");

    private final ReportReference reportReference;
    private final ReexaminationRepository reexaminationRepository;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public UUID execute(String reference, String text, LocalDate receivedOn, UUID actorId) {
        var kept = text == null ? "" : text.trim();
        if (kept.isEmpty() || kept.length() > 500) {
            throw new ReportRejected("reexamination-text", 400);
        }
        if (receivedOn == null || receivedOn.isAfter(LocalDate.now(clock.withZone(KINSHASA)))) {
            throw new ReportRejected("reexamination-date", 400);
        }
        var report = reportReference.require(reference);
        var decisions = jdbc.query("""
                select recorded_at from app.qualification
                where report_id = ?
                order by recorded_at desc, id desc
                limit 1
                """, (row, number) -> row.getTimestamp(1).toInstant().atZone(KINSHASA).toLocalDate(), report.id());
        var onTime = ReexaminationDeadline.onTime(decisions.isEmpty() ? null : decisions.get(0), receivedOn);
        var year = LocalDate.now(clock.withZone(KINSHASA)).getYear();
        var reexamination = new Reexamination(
                UUID.randomUUID(),
                "RX-" + year + "-" + "%06d".formatted(reexaminationRepository.nextNumber()),
                report.id(),
                report.reference(),
                kept,
                receivedOn,
                onTime,
                actorId,
                clock.instant());
        reexaminationRepository.save(reexamination);
        return reexamination.getId();
    }
}
