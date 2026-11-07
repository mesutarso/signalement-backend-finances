package cd.gouv.finances.signalements.report.taxpayer;

import cd.gouv.finances.signalements.report.dto.TaxpayerPageResponse;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadTaxpayersQuery {

    private static final int SIZE = 20;

    private final JdbcTemplate jdbc;

    public ReadTaxpayersQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public TaxpayerPageResponse execute(int page) {
        var requested = Math.max(page, 0);
        var total = jdbc.queryForObject("select count(*) from app.taxpayer", Long.class);
        var items = jdbc.query("""
                select id, tax_id, name, verified, recorded_at
                from app.taxpayer
                order by recorded_at desc, tax_id
                limit ? offset ?
                """, (row, number) -> new TaxpayerPageResponse.Item(
                row.getObject(1, UUID.class),
                row.getString(2),
                row.getString(3),
                row.getBoolean(4),
                row.getTimestamp(5).toInstant()), SIZE, requested * (long) SIZE);
        return new TaxpayerPageResponse(items, requested, SIZE, total == null ? 0 : total);
    }
}
