package cd.gouv.finances.signalements.report.sellerfile;

import cd.gouv.finances.signalements.report.dto.SellerFilePageResponse;
import cd.gouv.finances.signalements.report.sellerfile.SellerFileAssembly.Source;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadSellerFilesQuery {

    private static final int SIZE = 20;
    private static final Set<String> FILTERS = Set.of("identified", "unidentified");

    private final JdbcTemplate jdbc;

    public ReadSellerFilesQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public SellerFilePageResponse execute(String status, int page) {
        var identified = !"unidentified".equals(FILTERS.contains(status) ? status : "identified");
        var requested = Math.max(page, 0);
        var total = jdbc.queryForObject(identified ? identifiedCount() : unidentifiedCount(), Long.class);
        var args = new ArrayList<Object>();
        args.add(SIZE);
        args.add(requested * (long) SIZE);
        var sources = jdbc.query(identified ? identifiedPage() : unidentifiedPage(), (row, number) -> new Source(
                row.getObject(1, UUID.class),
                row.getString(2),
                row.getTimestamp(3).toLocalDateTime(),
                row.getString(4),
                row.getString(5),
                row.getString(6),
                row.getString(7),
                row.getString(8),
                row.getString(9),
                row.getString(10),
                row.getTimestamp(11).toInstant()), args.toArray());
        var items = identified ? SellerFileAssembly.identified(sources) : SellerFileAssembly.unidentified(sources);
        return new SellerFilePageResponse(items, requested, SIZE, total == null ? 0 : total);
    }

    private static String identifiedCount() {
        return "select count(*) from (select lower(trim(latest.seller_tax_id)) from (" + latest() + ") latest "
                + retained("latest")
                + " and nullif(trim(latest.seller_tax_id), '') is not null group by lower(trim(latest.seller_tax_id))) grouped";
    }

    private static String identifiedPage() {
        return "select report_id, identifier, reported_at, title, decision, finding, proof, seller_name, seller_tax_id, justification, qualified_at from ("
                + latest()
                + ") latest "
                + retained("latest")
                + " and lower(trim(latest.seller_tax_id)) in ("
                + "select lower(trim(page.seller_tax_id)) from ("
                + latest()
                + ") page "
                + retained("page")
                + " and nullif(trim(page.seller_tax_id), '') is not null group by lower(trim(page.seller_tax_id)) order by max(page.recorded_at) desc limit ? offset ?"
                + ") order by latest.recorded_at desc";
    }

    private static String unidentifiedCount() {
        return "select count(*) from (" + latest() + ") latest " + retained("latest") + " and nullif(trim(latest.seller_tax_id), '') is null";
    }

    private static String unidentifiedPage() {
        return "select report_id, identifier, reported_at, title, decision, finding, proof, seller_name, seller_tax_id, justification, qualified_at from ("
                + latest()
                + ") latest "
                + retained("latest")
                + " and nullif(trim(latest.seller_tax_id), '') is null order by latest.recorded_at desc limit ? offset ?";
    }

    private static String retained(String alias) {
        return "where " + alias + ".decision in ('confirm', 'wait')";
    }

    private static String latest() {
        return """
                select qualification.report_id,
                       report.external_id as identifier,
                       report.reported_at,
                       report.title,
                       qualification.decision,
                       qualification.finding,
                       qualification.proof,
                       qualification.seller_name,
                       qualification.seller_tax_id,
                       qualification.justification,
                       qualification.recorded_at as qualified_at,
                       qualification.recorded_at
                from (
                    select distinct on (report_id) *
                    from app.qualification
                    order by report_id, recorded_at desc
                ) qualification
                join app.report report on report.id = qualification.report_id
                """;
    }
}
