package cd.gouv.finances.signalements.report.query;

import cd.gouv.finances.signalements.report.ReportRejected;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReadAdministrationReports {
    private static final int SIZE = 20;
    private static final String REPORTS = """
            with reports as (
                select r.*, latest.seller_name as historical_name, latest.seller_tax_id as historical_tax_id,
                    case
                        when r.validation_state = 'validated' or latest.decision = 'confirm' then 'validated'
                        when (r.validation_state = 'rejected' or latest.decision = 'reject')
                            and (r.screening_reason = 'duplicate' or exists (
                                select 1 from app.duplicate_alert duplicate
                                where duplicate.status = 'confirmed'
                                    and (duplicate.report_id = r.id or duplicate.other_report_id = r.id)
                                    and duplicate.priority_report_id <> r.id
                            )) then 'duplicate'
                        when r.validation_state = 'rejected' or latest.decision = 'reject' then 'rejected'
                        else 'pending'
                    end as outcome
                from app.report r
                left join lateral (
                    select q.decision, q.seller_name, q.seller_tax_id from app.qualification q
                    where q.report_id = r.id and r.validation_state = 'legacy'
                    order by q.recorded_at desc, q.id desc limit 1
                ) latest on true
            )
            """;

    private final JdbcTemplate jdbc;

    @Value("${app.validation.analysis-enabled:true}")
    private boolean analysisEnabled;

    public record Page<T>(List<T> items, int page, int size, long total) { }
    public record Declarant(UUID reportId, String reference, String name, boolean identified,
                            long total, long validated, long validatedRewardAmount, long rejected, long duplicates, long pending) { }
    public record Company(String taxId, String name, long total, long validated, long rejected,
                          long pending, long duplicates, Boolean registryVerified) { }
    public record Payment(UUID reportId, String reference, int amount, String status, UUID statementId) { }
    public record Analysis(boolean enabled, long queued, long running, long completed, long failed) { }

    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public Page<Declarant> declarants(int page) {
        var grouped = REPORTS + """
                , grouped as (
                    select (array_agg(reports.id order by reported_at desc nulls last, reports.id desc))[1] as report_id,
                        (array_agg(reference_code order by reported_at desc nulls last, reports.id desc))[1] as reference,
                        (array_agg(reporter_name order by reported_at desc nulls last, reports.id desc)
                            filter (where nullif(btrim(reporter_name), '') is not null))[1] as name,
                        nullif(btrim(reporter_user_id), '') is not null as identified,
                        count(*) as total, count(*) filter (where outcome = 'validated') as validated,
                        coalesce(sum(reward.amount) filter (where outcome = 'validated'), 0) as validated_reward_amount,
                        count(*) filter (where outcome = 'rejected') as rejected,
                        count(*) filter (where outcome = 'duplicate') as duplicates,
                        count(*) filter (where outcome = 'pending') as pending
                    from reports
                    left join app.prime reward on reward.report_id = reports.id and reward.status <> 'withdrawn'
                    group by nullif(btrim(reporter_user_id), ''),
                        case when nullif(btrim(reporter_user_id), '') is null then reports.id end
                )
                """;
        return page(grouped, "grouped", "total desc, report_id", page, (r, n) -> new Declarant(
                r.getObject("report_id", UUID.class), r.getString("reference"), r.getString("name"), r.getBoolean("identified"),
                r.getLong("total"), r.getLong("validated"), r.getLong("validated_reward_amount"),
                r.getLong("rejected"), r.getLong("duplicates"), r.getLong("pending")));
    }

    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public Page<Company> companies(int page) {
        return companyPage(page, false);
    }

    @PreAuthorize("hasAuthority('dossier.read')")
    public Page<Company> establishments(int page) {
        return companyPage(page, true);
    }

    private Page<Company> companyPage(int page, boolean includeRegistryOnly) {
        var registryJoin = includeRegistryOnly ? "full join" : "left join";
        var grouped = REPORTS + """
                , sellers as (
                    select r.id, outcome, r.reported_at,
                        upper(coalesce(nullif(btrim(a.seller_tax_id), ''), nullif(btrim(historical_tax_id), ''))) as tax_id,
                        coalesce(nullif(btrim(a.seller_name), ''), nullif(btrim(historical_name), '')) as seller_name
                    from reports r left join app.report_assessment a on a.report_id = r.id
                ), grouped as (
                    select tax_id,
                        (array_agg(seller_name order by reported_at desc nulls last, id desc)
                            filter (where seller_name is not null))[1] as name,
                        count(*) filter (where outcome <> 'duplicate') as total,
                        count(*) filter (where outcome = 'validated') as validated,
                        count(*) filter (where outcome = 'rejected') as rejected,
                        count(*) filter (where outcome = 'pending') as pending,
                        count(*) filter (where outcome = 'duplicate') as duplicates
                    from sellers
                    where tax_id is not null or seller_name is not null
                    group by tax_id, case when tax_id is null then lower(seller_name) end
                ), companies as (
                    select coalesce(c.tax_id, upper(btrim(t.tax_id))) as tax_id,
                        coalesce(c.name, nullif(btrim(t.name), '')) as name,
                        coalesce(c.total, 0) as total, coalesce(c.validated, 0) as validated,
                        coalesce(c.rejected, 0) as rejected, coalesce(c.pending, 0) as pending,
                        coalesce(c.duplicates, 0) as duplicates, t.verified as registry_verified
                    from grouped c %s app.taxpayer t on lower(c.tax_id) = lower(btrim(t.tax_id))
                )
                """.formatted(registryJoin);
        var order = includeRegistryOnly ? "(registry_verified is not null) desc, total desc, tax_id, name"
                : "total desc, tax_id, name";
        return page(grouped, "companies", order, page, (r, n) -> new Company(
                r.getString("tax_id"), r.getString("name"), r.getLong("total"), r.getLong("validated"),
                r.getLong("rejected"), r.getLong("pending"), r.getLong("duplicates"),
                r.getObject("registry_verified", Boolean.class)));
    }

    @PreAuthorize("hasAuthority('payment.read') and hasAuthority('report.read')")
    public Page<Payment> payments(int page, UUID reportId, String status) {
        if (status != null && !Set.of("preparing", "to-pay", "pending", "paid", "failed", "unknown").contains(status)) {
            throw new ReportRejected("validation", 400);
        }
        var payments = """
                with payments as (
                    select r.id as report_id, r.reference_code, coalesce(line.amount, p.amount) as amount,
                        line.statement_id, r.reported_at,
                        case
                            when attempt.result = 'paid' or transfer.state = 'paid' then 'paid'
                            when transfer.state in ('submitting', 'pending') then 'pending'
                            when transfer.state = 'unknown' or attempt.result = 'unknown' and transfer.state is null then 'unknown'
                            when transfer.state = 'failed' or attempt.result = 'failed' then 'failed'
                            when statement.status = 'authorized' then 'to-pay'
                            else 'preparing'
                        end as status
                    from app.prime p join app.report r on r.id = p.report_id
                    left join app.payment_statement_line line on line.prime_id = p.id and not line.released
                    left join app.payment_statement statement on statement.id = line.statement_id
                    left join lateral (
                        select result from app.payment_attempt
                        where line_id = line.id and left(coalesce(reference_code, ''), 4) <> 'SIM-'
                        order by (result = 'paid') desc, recorded_at desc, id desc limit 1
                    ) attempt on true
                    left join lateral (
                        select state from app.payment_transfer
                        where line_id = line.id and environment = 'production'
                        order by started_at desc, id desc limit 1
                    ) transfer on true
                    where (p.status = 'approved' and r.validation_state = 'validated'
                        or attempt.result = 'paid' or transfer.state in ('paid', 'submitting', 'pending', 'unknown'))
                    %s
                ), filtered as (select * from payments %s)
                """.formatted(reportId == null ? "" : "and r.id = ?", status == null ? "" : "where status = ?");
        var args = new ArrayList<>();
        if (reportId != null) args.add(reportId);
        if (status != null) args.add(status);
        return page(payments, "filtered", "reported_at desc nulls last, report_id", page, (r, n) -> new Payment(
                r.getObject("report_id", UUID.class), r.getString("reference_code"), r.getInt("amount"),
                r.getString("status"), r.getObject("statement_id", UUID.class)), args.toArray());
    }

    @PreAuthorize("hasAuthority('import.read')")
    public Analysis analysis() {
        return jdbc.queryForObject("""
                select count(*) filter (where job_state = 'queued') as queued,
                    count(*) filter (where job_state = 'running') as running,
                    count(*) filter (where job_state = 'done' and error_code is null) as completed,
                    count(*) filter (where job_state = 'done' and error_code is not null) as failed
                from app.report_assessment
                """, (row, n) -> new Analysis(analysisEnabled, row.getLong("queued"), row.getLong("running"),
                row.getLong("completed"), row.getLong("failed")));
    }

    private <T> Page<T> page(String ctes, String relation, String order, int requested, RowMapper<T> row, Object... parameters) {
        var page = Math.max(0, requested);
        var total = jdbc.queryForObject(ctes + "select count(*) from " + relation, Long.class, parameters);
        if (total == null || total == 0) return new Page<>(List.of(), page, SIZE, 0);
        var args = new ArrayList<>(List.of(parameters));
        args.add(SIZE);
        args.add(page * (long) SIZE);
        var items = jdbc.query(ctes + "select * from " + relation + " order by " + order + " limit ? offset ?", row, args.toArray());
        return new Page<>(items, page, SIZE, total);
    }
}
