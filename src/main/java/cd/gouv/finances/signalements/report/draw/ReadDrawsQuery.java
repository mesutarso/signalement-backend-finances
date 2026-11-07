package cd.gouv.finances.signalements.report.draw;

import cd.gouv.finances.signalements.report.dto.DrawPageResponse;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadDrawsQuery {

    private final JdbcTemplate jdbc;

    public ReadDrawsQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public DrawPageResponse execute() {
        var tickets = jdbc.query("""
                select id, report_reference, kind, period_start
                from app.draw_ticket
                where excluded_at is null
                order by period_start desc, kind, report_reference
                limit 20
                """, (row, number) -> new DrawPageResponse.Ticket(
                row.getObject(1, UUID.class), row.getString(2), row.getString(3), row.getDate(4).toLocalDate()));
        var draws = jdbc.query("""
                select id, reference_code, kind, period_start, witness, status
                from app.campaign_draw
                order by drawn_at desc
                limit 20
                """, (row, number) -> new DrawPageResponse.Draw(
                row.getObject(1, UUID.class), row.getString(2), row.getString(3), row.getDate(4).toLocalDate(), row.getString(5), row.getString(6)));
        var results = jdbc.query("""
                select draw.id, draw.reference_code, prize.label, prize.report_reference
                from app.draw_prize prize
                join app.campaign_draw draw on draw.id = prize.draw_id
                where draw.status = 'published'
                order by draw.published_at desc, prize.label, prize.report_reference
                limit 50
                """, (row, number) -> new DrawPageResponse.Result(
                row.getObject(1, UUID.class), row.getString(2), row.getString(3), row.getString(4)));
        var prizes = jdbc.query("""
                select prize.id, draw.reference_code, prize.label, prize.report_reference, prize.handed_at is not null
                from app.draw_prize prize
                join app.campaign_draw draw on draw.id = prize.draw_id
                order by draw.drawn_at desc, prize.label, prize.report_reference
                limit 50
                """, (row, number) -> new DrawPageResponse.Prize(
                row.getObject(1, UUID.class), row.getString(2), row.getString(3), row.getString(4), row.getBoolean(5)));
        return new DrawPageResponse(tickets, draws, results, prizes);
    }
}
