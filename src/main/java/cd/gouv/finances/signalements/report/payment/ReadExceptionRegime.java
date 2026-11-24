package cd.gouv.finances.signalements.report.payment;

import cd.gouv.finances.signalements.araka.ArakaProperties;
import cd.gouv.finances.signalements.report.ReportRejected;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReadExceptionRegime {
    private static final int SIZE = 20;
    private static final String PERIOD = "(?::date is null or r.reported_on >= ?) and (?::date is null or r.reported_on <= ?)";
    private static final String SORT = " order by reported_on desc, reference_code, id";

    private static final String FACTS = """
            with facts as (
                select r.id, r.reference_code, r.reported_on, r.reporter_name, a.seller_name,
                    r.validation_state, q.finding, coalesce(a.reason, q.justification) as reason,
                    p.id as prime_id, p.amount, p.phone, l.id as line_id,
                    t.id as transfer_id, t.reference as transfer_reference, queued.failure_code,
                    coalesce(a.unique_invoice = 'duplicate', false) or exists (
                        select 1 from app.duplicate_alert d where (d.report_id = r.id or d.other_report_id = r.id)
                        and (d.status = 'open' or (d.status = 'confirmed' and d.priority_report_id <> r.id))
                    ) as duplicate,
                    case
                        when exists (select 1 from app.payment_statement_line pl join app.payment_attempt pa on pa.line_id = pl.id
                            where pl.prime_id = p.id and pa.result = 'paid' and pa.reference_code not like 'SIM-%%') then 'paid'
                        when exists (select 1 from app.payment_statement_line pl join app.payment_transfer pt on pt.line_id = pl.id
                            where pl.prime_id = p.id and pt.environment = 'production'
                              and pt.state in ('submitting', 'pending', 'unknown', 'paid')) then
                            case when t.state in ('submitting', 'pending', 'unknown', 'paid') then t.state else 'pending' end
                        when queued.state = 'queued' then 'queued'
                        when t.state in ('submitting', 'pending', 'unknown') then t.state
                        when t.state = 'paid' then 'test-paid'
                    end as active_payment,
                    case
                        when r.validation_state <> 'validated' then 'unvalidated'
                        when p.status is null or p.status not in ('proposed', 'approved') then 'reward'
                        when (select coalesce(sum(base_points), 0) from app.point_movement where report_id = r.id) > 0
                            then 'legacy-reward'
                        when p.amount > 999999 then 'amount'
                        when l.id is not null and not s.exceptional then 'standard-statement'
                        when ba.declarant_id is null then 'identity'
                        when (select action from app.prime_suspension where declarant_id = p.declarant_id
                              order by recorded_at desc, id desc limit 1) = 'suspended' then 'suspended'
                        when coalesce(p.phone, '') = ''
                          or (l.id is not null and (l.phone is distinct from p.phone or l.amount <> p.amount))
                          or (pn.state is not null and (pn.state <> 'ready' or pn.phone is distinct from p.phone))
                          or (pn.state is null and p.status = 'approved')
                          or exists (select 1 from app.prime sibling join app.report sr on sr.id = sibling.report_id
                                where sibling.declarant_id = p.declarant_id and sibling.status <> 'withdrawn'
                                  and nullif(btrim(sr.reporter_phone), '') is not null
                                  and btrim(sr.reporter_phone) is distinct from p.phone)
                            then 'phone'
                    end as blocker
                from app.report r
                left join app.report_assessment a on a.report_id = r.id
                left join app.prime p on p.report_id = r.id
                left join app.beneficiary_account ba on ba.declarant_id = p.declarant_id
                left join app.payout_number pn on pn.declarant_id = p.declarant_id
                left join app.payment_statement_line l on l.prime_id = p.id and not l.released
                left join app.payment_statement s on s.id = l.statement_id
                left join lateral (
                    select finding, justification from app.qualification where report_id = r.id
                    order by recorded_at desc, id desc limit 1
                ) q on true
                left join lateral (
                    select id, state, reference from app.payment_transfer where line_id = l.id and environment = ?
                    order by started_at desc, id desc limit 1
                ) t on true
                left join lateral (
                    select i.state, i.failure_code from app.exception_payment_item i
                    join app.exception_payment_batch b on b.id = i.batch_id
                    where i.line_id = l.id and (i.state = 'queued' or b.environment = ?)
                    order by (i.state = 'queued') desc, b.created_at desc, i.id desc limit 1
                ) queued on true
                where %s
            ), reports as (
                select *, coalesce(active_payment, case when duplicate then 'duplicate' end, blocker,
                    case when failure_code is not null then 'blocked' when transfer_id is not null then 'failed' end,
                    'ready') as payment_state
                from facts
            )
            """;

    private final JdbcTemplate jdbc;
    private final ArakaProperties araka;

    public record Period(LocalDate start, LocalDate end) {
        public Period {
            if (start != null && end != null && start.isAfter(end)) throw new ReportRejected("period", 400);
        }
    }
    public record Report(UUID id, String reference, LocalDate date, String declarant, String company, String state,
            String finding, String reason, Integer amount, String paymentState, String paymentReference, String paymentError) { }
    public record Page(List<Report> items, long total, long validatable, int page, int size, String environment, boolean configured) { }
    public record Candidate(UUID id, String reference, String phone, int amount, UUID primeId, UUID lineId,
            UUID previousTransferId) { }

    @Transactional(readOnly = true)
    public Page page(Period period, int page) {
        var summary = jdbc.queryForObject("""
                select count(*), count(*) filter (where r.validation_state <> 'validated')
                from app.report r where
                """ + PERIOD, (row, number) -> new long[] {row.getLong(1), row.getLong(2)},
                period.start(), period.start(), period.end(), period.end());
        var actualPage = (int) Math.max(0, Math.min(page, (summary[0] - 1) / SIZE));
        var rows = jdbc.query(FACTS.formatted(PERIOD) + "select * from reports" + SORT + " limit ? offset ?",
                this::report, environment(), environment(),
                period.start(), period.start(), period.end(), period.end(), SIZE, (long) actualPage * SIZE);
        return new Page(rows, summary[0], summary[1], actualPage, SIZE, environment(), araka.configured());
    }

    public List<UUID> pendingValidation(Period period) {
        return jdbc.queryForList("select r.id from app.report r where r.validation_state <> 'validated' and " + PERIOD
                + " order by r.reported_on, r.id", UUID.class, period.start(), period.start(), period.end(), period.end());
    }

    public List<Candidate> candidates(Period period) {
        return jdbc.query(FACTS.formatted(PERIOD)
                + "select * from reports where payment_state in ('ready','failed','blocked')" + SORT,
                ReadExceptionRegime::candidate, environment(), environment(), period.start(), period.start(), period.end(), period.end())
                .stream().filter(candidate -> allowedWallet(ArakaProperties.wallet(candidate.phone()))).toList();
    }

    Candidate candidate(UUID report) {
        var rows = jdbc.query(FACTS.formatted("r.id = ?")
                + "select * from reports where payment_state in ('ready','failed','blocked')",
                ReadExceptionRegime::candidate, environment(), environment(), report);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    String environment() { return araka.sandbox() ? "sandbox" : "production"; }

    private Report report(ResultSet row, int index) throws SQLException {
        var status = row.getString("payment_state");
        if (isPayableStatus(status)) {
            var wallet = ArakaProperties.wallet(row.getString("phone"));
            if (wallet == null) status = "phone";
            else if (araka.sandbox() && !allowedWallet(wallet)) status = "test-wallet";
        }
        return new Report(row.getObject("id", UUID.class), row.getString("reference_code"),
                row.getObject("reported_on", LocalDate.class), row.getString("reporter_name"), row.getString("seller_name"),
                row.getString("validation_state"), row.getString("finding"), row.getString("reason"),
                row.getObject("amount", Integer.class), status, row.getString("transfer_reference"),
                row.getString("failure_code"));
    }

    private static boolean isPayableStatus(String status) {
        return List.of("ready", "failed", "blocked").contains(status);
    }

    private boolean allowedWallet(String wallet) {
        return wallet != null && List.of("MPESA", "AIRTEL", "ORANGE", "AFRIMONEY").stream()
                .anyMatch(provider -> araka.permits(wallet, provider));
    }

    private static Candidate candidate(ResultSet row, int index) throws SQLException {
        return new Candidate(row.getObject("id", UUID.class), row.getString("reference_code"), row.getString("phone"),
                row.getInt("amount"), row.getObject("prime_id", UUID.class), row.getObject("line_id", UUID.class),
                row.getObject("transfer_id", UUID.class));
    }
}
