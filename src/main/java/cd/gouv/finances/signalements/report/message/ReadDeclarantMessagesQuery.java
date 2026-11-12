package cd.gouv.finances.signalements.report.message;

import cd.gouv.finances.signalements.report.dto.DeclarantMessagePageResponse;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadDeclarantMessagesQuery {

    private static final int SIZE = 20;

    private final JdbcTemplate jdbc;

    public ReadDeclarantMessagesQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public DeclarantMessagePageResponse execute(int page) {
        var requested = Math.max(page, 0);
        var total = jdbc.queryForObject("select count(*) from app.declarant_message", Long.class);
        var items = jdbc.query("""
                select id, report_reference, kind, body, prepared_at,
                       case when status = 'sending' and send_started_at < current_timestamp - interval '30 seconds'
                         then 'unknown' else status end, sent_at, delivery_code
                from app.declarant_message
                order by prepared_at desc
                limit ? offset ?
                """, (row, number) -> new DeclarantMessagePageResponse.Item(
                row.getObject(1, UUID.class),
                row.getString(2),
                row.getString(3),
                row.getString(4),
                row.getTimestamp(5).toInstant(), row.getString(6),
                row.getTimestamp(7) == null ? null : row.getTimestamp(7).toInstant(), row.getString(8)), SIZE, requested * (long) SIZE);
        return new DeclarantMessagePageResponse(items, requested, SIZE, total == null ? 0 : total);
    }
}
