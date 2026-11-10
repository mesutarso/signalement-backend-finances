package cd.gouv.finances.signalements.report.letter;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.LetterLine;
import cd.gouv.finances.signalements.report.repository.LetterLineRepository;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class WeeklyLettersCommand {
    private final JdbcTemplate jdbc;
    private final LetterSourceLoader source;
    private final LetterLineRepository lines;
    private final RecordAudit audit;
    private final Clock clock;

    public record Route(String taxId, String service) { }
    public record DispatchInfo(String service, LocalDate sentOn, String reference) { }

    @PreAuthorize("hasAnyRole('SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public DispatchInfo dispatchInfo(UUID id) {
        var rows = jdbc.query("""
                select letter.service_name, dispatch.sent_on, dispatch.dispatch_reference
                from app.letter letter left join app.letter_dispatch dispatch on dispatch.letter_id = letter.id where letter.id = ?
                """, (row, number) -> new DispatchInfo(row.getString(1), row.getDate(2) == null ? null : row.getDate(2).toLocalDate(), row.getString(3)), id);
        if (rows.isEmpty()) throw new ReportRejected("letter-absent", 404);
        return rows.getFirst();
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public List<Route> routes() {
        return jdbc.query("select tax_id, service_name from app.letter_route order by service_name, tax_id",
                (row, number) -> new Route(row.getString(1), row.getString(2)));
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public void route(String taxId, String service, UUID actor) {
        var tax = taxId == null ? "" : taxId.trim().toUpperCase(Locale.ROOT);
        var name = service == null ? "" : service.trim();
        if (tax.isEmpty() || tax.length() > 40 || name.isEmpty() || name.length() > 160) throw new ReportRejected("letter-routing", 400);
        jdbc.update("""
                insert into app.letter_route (tax_id, service_name, actor_id, recorded_at) values (?, ?, ?, ?)
                on conflict (tax_id) do update set service_name = excluded.service_name,
                    actor_id = excluded.actor_id, recorded_at = excluded.recorded_at
                """, tax, name, actor, java.sql.Timestamp.from(clock.instant()));
        audit.allowed(actor, "letter-routing", "taxpayer", tax);
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public List<UUID> prepare(UUID actor) {
        jdbc.queryForObject("select pg_advisory_xact_lock(783962141)", Object.class);
        var routing = new HashMap<String, String>();
        routes().forEach(route -> routing.put(route.taxId(), route.service()));
        var groups = new TreeMap<String, List<LetterContent.Source>>();
        for (var finding : source.findings()) {
            var service = routing.get(finding.taxId().trim().toUpperCase(Locale.ROOT));
            if (service == null) throw new ReportRejected("letter-routing", 409);
            groups.computeIfAbsent(service, ignored -> new ArrayList<>()).add(finding);
        }
        var today = LocalDate.now(clock.withZone(ZoneId.of("Africa/Kinshasa")));
        var monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var ids = new ArrayList<UUID>();
        for (var group : groups.entrySet()) {
            var existing = jdbc.queryForList("select id from app.letter where period_start = ? and service_name = ? and status in ('draft', 'validated')", UUID.class, monday, group.getKey());
            if (!existing.isEmpty()) { ids.add(existing.getFirst()); continue; }
            var id = UUID.randomUUID();
            var sequence = jdbc.queryForObject("select nextval('app.letter_seq')", Long.class);
            jdbc.update("""
                    insert into app.letter (id, reference_code, period_start, period_end, status, actor_id, prepared_at, service_name)
                    values (?, ?, ?, ?, 'draft', ?, ?, ?)
                    """, id, "CO-" + today.getYear() + "-" + "%06d".formatted(sequence), monday, monday.plusDays(6), actor,
                    java.sql.Timestamp.from(clock.instant()), group.getKey());
            var photos = source.photos(group.getValue().stream().map(LetterContent.Source::reportId).distinct().toList());
            int position = 0;
            for (var line : LetterContent.compose(group.getValue(), photos)) {
                lines.save(new LetterLine(UUID.randomUUID(), id, line.annex(), position++, line.reportId(), line.reportReference(),
                        line.taxId(), line.sellerName(), line.decision(), line.finding(), line.proof(), line.documentNumber(),
                        line.issuedOn(), line.amount(), line.justification(), line.missingElements(), line.dgiReference(), line.photoId(), line.contentType()));
            }
            audit.allowed(actor, "letter-weekly", "letter", id.toString());
            ids.add(id);
        }
        return List.copyOf(ids);
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public void dispatch(UUID id, LocalDate sentOn, String reference, UUID actor) {
        var kept = reference == null ? "" : reference.trim();
        var today = LocalDate.now(clock.withZone(ZoneId.of("Africa/Kinshasa")));
        if (sentOn == null || sentOn.isAfter(today) || kept.isEmpty() || kept.length() > 160) throw new ReportRejected("letter-dispatch", 400);
        var valid = jdbc.queryForObject("select exists(select 1 from app.letter where id = ? and status = 'validated')", Boolean.class, id);
        if (!Boolean.TRUE.equals(valid)) throw new ReportRejected("letter-dispatch", 409);
        var created = jdbc.update("""
                insert into app.letter_dispatch (letter_id, dispatch_reference, sent_on, actor_id, recorded_at) values (?, ?, ?, ?, ?)
                on conflict (letter_id) do nothing
                """, id, kept, sentOn, actor, java.sql.Timestamp.from(clock.instant()));
        if (created == 0) {
            var same = jdbc.queryForObject("select sent_on = ? and dispatch_reference = ? from app.letter_dispatch where letter_id = ?", Boolean.class, sentOn, kept, id);
            if (!Boolean.TRUE.equals(same)) throw new ReportRejected("letter-dispatch", 409);
        } else audit.allowed(actor, "letter-dispatch", "letter", id.toString());
    }
}
