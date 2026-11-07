package cd.gouv.finances.signalements.report.draw;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.DrawTicket;
import cd.gouv.finances.signalements.report.repository.DrawTicketRepository;
import java.sql.Timestamp;
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
public class PrepareDrawTicketsCommand {

    private static final ZoneId KINSHASA = ZoneId.of("Africa/Kinshasa");

    private final JdbcTemplate jdbc;
    private final DrawTicketRepository drawTicketRepository;

    public record Confirmed(UUID reportId, String reference, String personKey) {
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public int execute(LocalDate month) {
        if (month == null || month.getDayOfMonth() != 1) {
            throw new ReportRejected("draw-month", 400);
        }
        var from = month.atStartOfDay(KINSHASA).toInstant();
        var to = month.plusMonths(1).atStartOfDay(KINSHASA).toInstant();
        var confirmed = jdbc.query("""
                select report.id, report.reference_code, report.reporter_user_id
                from app.report report
                join lateral (
                    select qualification.decision, qualification.recorded_at
                    from app.qualification qualification
                    where qualification.report_id = report.id
                    order by qualification.recorded_at desc, qualification.id desc
                    limit 1
                ) latest on latest.decision = 'confirm'
                where latest.recorded_at >= ? and latest.recorded_at < ?
                order by report.reference_code, report.id
                """, (row, number) -> {
            var reportId = row.getObject(1, UUID.class);
            var person = row.getString(3);
            var key = person == null || person.isBlank() ? "report:" + reportId : person.trim();
            return new Confirmed(reportId, row.getString(2), key);
        }, Timestamp.from(from), Timestamp.from(to));
        if (confirmed.isEmpty()) {
            throw new ReportRejected("draw-empty", 404);
        }
        var created = 0;
        for (var report : confirmed) {
            created += keep(report, DrawTicket.MONTHLY, month);
            created += keep(report, DrawTicket.FINAL, LocalDate.of(2027, 4, 1));
        }
        return created;
    }

    private int keep(Confirmed report, String kind, LocalDate periodStart) {
        if (drawTicketRepository.existsByReportIdAndKind(report.reportId(), kind)) {
            return 0;
        }
        drawTicketRepository.save(new DrawTicket(UUID.randomUUID(), report.reportId(), report.reference(), report.personKey(), kind, periodStart));
        return 1;
    }
}
