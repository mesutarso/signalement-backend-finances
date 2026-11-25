package cd.gouv.finances.signalements.report.export;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.letter.LetterAnnexDocument;
import cd.gouv.finances.signalements.report.query.ReadAdministrationReports;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('report.export')")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReadOperationalReports {
    private static final int PAGE_SIZE = 20;
    private static final Map<String, String> STATUSES = Map.of(
            "validated", "Validé", "rejected", "Rejeté", "duplicate", "Doublon",
            "untreated", "Non traité", "doubt", "À vérifier");
    private static final String ORDER = " order by received_at desc, id";
    private static final String DETAILS = ReadAdministrationReports.REPORTS_WITH_OUTCOME + """
            , evidence as (
                select r.id, r.reported_on, r.reported_at as received_at,
                    case when r.outcome <> 'pending' then r.outcome
                        when r.validation_state in ('queued', 'analyzing') then 'untreated'
                        else 'doubt' end as status,
                    case when r.outcome = 'validated' and latest.decision = 'confirm' then latest.finding end as finding,
                    upper(coalesce(nullif(btrim(a.seller_tax_id), ''), nullif(btrim(latest.seller_tax_id), ''))) as tax_id,
                    coalesce(nullif(btrim(a.seller_name), ''), nullif(btrim(latest.seller_name), '')) as company,
                    coalesce(nullif(btrim(a.document_number), ''), nullif(btrim(latest.document_number), '')) as invoice_number,
                    coalesce(a.document_date, latest.issued_on) as invoice_date,
                    coalesce(nullif(btrim(a.extracted->>'totalAmount'), ''), latest.amount) as amount,
                    nullif(upper(btrim(a.extracted->>'currency')), '') as currency
                from reports r
                left join app.report_assessment a on a.report_id=r.id
                left join lateral (
                    select decision, finding, seller_tax_id, seller_name, document_number, issued_on, amount
                    from app.qualification q where q.report_id=r.id
                    order by recorded_at desc, id desc limit 1
                ) latest on r.outcome <> 'pending' or r.validation_state = 'legacy'
            ), details as (
                select evidence.*, route.service_name as service
                from evidence left join app.letter_route route on route.tax_id=evidence.tax_id
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public record Filters(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          String service, String finding, String company, String status) { }
    public record Options(List<String> services, List<String> findings) { }
    public record Row(UUID id, LocalDateTime receivedAt, String company, String taxId, String service,
                      String status, String finding, String invoiceNumber, LocalDate invoiceDate, String amount, String currency) { }
    private record Selection(String sql, MapSqlParameterSource params) { }

    public Options options() {
        var services = jdbc.queryForList("select distinct service_name from app.letter_route order by service_name", Map.of(), String.class);
        var findings = jdbc.queryForList(DETAILS + "select distinct finding from details where finding is not null order by finding",
                Map.of(), String.class);
        return new Options(services, findings);
    }

    public ReadAdministrationReports.Page<Row> preview(Filters filters, int requestedPage) {
        var selection = select(filters);
        var total = jdbc.queryForObject(selection.sql() + "select count(*) from selected", selection.params(), Long.class);
        if (total == null || total == 0) return new ReadAdministrationReports.Page<>(List.of(), 0, PAGE_SIZE, 0);
        var page = (int) Math.max(0, Math.min(requestedPage, (total - 1) / PAGE_SIZE));
        selection.params().addValue("size", PAGE_SIZE).addValue("offset", (long) page * PAGE_SIZE);
        var rows = jdbc.query(selection.sql() + "select * from selected" + ORDER + " limit :size offset :offset",
                selection.params(), ReadOperationalReports::row);
        return new ReadAdministrationReports.Page<>(rows, page, PAGE_SIZE, total);
    }

    public byte[] csv(Filters filters) {
        var selection = select(filters);
        var csv = new StringBuilder("\uFEFF");
        CsvRows.append(csv, ';', "Reçu le", "Entreprise", "NIF", "Service opérationnel", "Statut", "Type de signalement",
                "Numéro de facture", "Date de facture", "Montant TTC", "Devise");
        jdbc.query(selection.sql() + "select * from selected" + ORDER, selection.params(), (RowCallbackHandler) row -> {
            var report = row(row, 0);
            CsvRows.append(csv, ';', report.receivedAt().toString().replace('T', ' '), report.company(), report.taxId(),
                    report.service(), STATUSES.get(report.status()), LetterAnnexDocument.findingLabel(report.finding()),
                    report.invoiceNumber(), report.invoiceDate() == null ? null : report.invoiceDate().toString(),
                    report.amount(), report.currency());
        });
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Selection select(Filters filters) {
        if (filters.from() != null && filters.to() != null && filters.from().isAfter(filters.to())) {
            throw new ReportRejected("period", 400);
        }
        var status = clean(filters.status());
        if (status == null) status = "validated";
        if (!"all".equals(status) && !STATUSES.containsKey(status)) throw new ReportRejected("validation", 400);

        var statements = new StringBuilder(DETAILS).append(", selected as (select * from details where true");
        var values = new MapSqlParameterSource();
        add(statements, values, " and status=:status", "status", "all".equals(status) ? null : status);
        add(statements, values, " and reported_on>=:from", "from", filters.from());
        add(statements, values, " and reported_on<=:to", "to", filters.to());
        add(statements, values, " and service=:service", "service", clean(filters.service()));
        add(statements, values, " and finding=:finding", "finding", clean(filters.finding()));
        add(statements, values, " and (strpos(lower(company), lower(:company))>0 or strpos(lower(tax_id), lower(:company))>0)",
                "company", clean(filters.company()));
        return new Selection(statements.append(") ").toString(), values);
    }

    private static void add(StringBuilder sql, MapSqlParameterSource values, String condition, String name, Object value) {
        if (value != null) {
            sql.append(condition);
            values.addValue(name, value);
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static Row row(ResultSet row, int index) throws SQLException {
        return new Row(row.getObject("id", UUID.class), row.getObject("received_at", LocalDateTime.class),
                row.getString("company"), row.getString("tax_id"), row.getString("service"), row.getString("status"),
                row.getString("finding"), row.getString("invoice_number"), row.getObject("invoice_date", LocalDate.class),
                row.getString("amount"), row.getString("currency"));
    }
}
