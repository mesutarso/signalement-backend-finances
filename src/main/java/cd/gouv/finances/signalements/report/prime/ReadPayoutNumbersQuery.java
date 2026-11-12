package cd.gouv.finances.signalements.report.prime;

import cd.gouv.finances.signalements.report.dto.PayoutNumberResponse;
import cd.gouv.finances.signalements.report.dto.PayoutPageResponse;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadPayoutNumbersQuery {

    private static final int SIZE = 20;

    private final JdbcTemplate jdbc;

    public ReadPayoutNumbersQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public PayoutPageResponse execute(int page) {
        var requested = Math.max(page, 0);
        var total = jdbc.queryForObject("select count(*) from app.payout_number p where exists (select 1 from app.prime r where r.declarant_id=p.declarant_id and r.report_id is not null)", Long.class);
        var items = jdbc.query("""
                select id, phone, state, updated_at
                from app.payout_number p where exists (select 1 from app.prime r where r.declarant_id=p.declarant_id and r.report_id is not null)
                order by updated_at desc
                limit ? offset ?
                """, (row, number) -> new PayoutNumberResponse(
                row.getObject(1, UUID.class),
                row.getString(2),
                row.getString(3),
                row.getTimestamp(4).toInstant()), SIZE, requested * (long) SIZE);
        return new PayoutPageResponse(items, requested, SIZE, total == null ? 0 : total);
    }
}
