package cd.gouv.finances.signalements.report.abuse;

import cd.gouv.finances.signalements.report.dto.PrimeSuspensionResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadPrimeSuspensionsQuery {

    private final JdbcTemplate jdbc;

    public ReadPrimeSuspensionsQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public PrimeSuspensionResponse execute() {
        var items = jdbc.query("""
                select action, reason, recorded_at, declarant_id
                from (
                    select distinct on (declarant_id) action, reason, recorded_at, declarant_id
                    from app.prime_suspension
                    order by declarant_id, recorded_at desc
                ) latest
                order by recorded_at desc
                limit 20
                """, (row, number) -> new PrimeSuspensionResponse.Item(
                reference(row.getString(4)),
                row.getString(1),
                row.getString(2),
                row.getTimestamp(3).toInstant()));
        return new PrimeSuspensionResponse(items);
    }

    private String reference(String declarantId) {
        var rows = jdbc.query("""
                select reference_code from app.report
                where btrim(reporter_user_id) = ?
                order by reported_at desc
                limit 1
                """, (row, number) -> row.getString(1), declarantId);
        return rows.isEmpty() ? "" : rows.getFirst();
    }
}
