package cd.gouv.finances.signalements.report.letter;

import cd.gouv.finances.signalements.report.dto.LetterPageResponse;
import cd.gouv.finances.signalements.report.dto.LetterSummaryResponse;
import java.util.ArrayList;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadLettersQuery {

    private static final int SIZE = 20;

    private final JdbcTemplate jdbc;

    public ReadLettersQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public LetterPageResponse execute(String status, int page) {
        var waiting = "waiting".equals(status);
        var requested = Math.max(page, 0);
        var where = waiting
                ? "where letter.status = 'validated' and exists (select 1 from app.letter_dispatch dispatch where dispatch.letter_id = letter.id) and not exists (select 1 from app.letter_response response where response.letter_id = letter.id) "
                : "";
        var total = jdbc.queryForObject("select count(*) from app.letter letter " + where, Long.class);
        var args = new ArrayList<Object>();
        args.add(SIZE);
        args.add(requested * (long) SIZE);
        var items = jdbc.query("""
                select letter.id, letter.reference_code, letter.period_start, letter.period_end, letter.status, letter.recipient, letter.reason, letter.prepared_at, letter.decided_at
                from app.letter letter
                """ + where + """
                order by letter.prepared_at desc
                limit ? offset ?
                """, (row, number) -> new LetterSummaryResponse(
                row.getObject(1, java.util.UUID.class),
                row.getString(2),
                row.getDate(3).toLocalDate(),
                row.getDate(4).toLocalDate(),
                row.getString(5),
                row.getString(6),
                row.getString(7),
                row.getTimestamp(8).toInstant(),
                row.getTimestamp(9) == null ? null : row.getTimestamp(9).toInstant()), args.toArray());
        return new LetterPageResponse(items, requested, SIZE, total == null ? 0 : total);
    }
}
