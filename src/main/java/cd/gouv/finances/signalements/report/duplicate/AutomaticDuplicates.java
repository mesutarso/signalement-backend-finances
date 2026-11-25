package cd.gouv.finances.signalements.report.duplicate;

import cd.gouv.finances.signalements.report.validation.AssessmentDecision;
import cd.gouv.finances.signalements.report.validation.ValidationRules;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Exact attachment sets or complete matching invoice keys, never a seller name/NIF alone. */
@Component
@RequiredArgsConstructor
public class AutomaticDuplicates {
    private final JdbcTemplate jdbc;
    private final PrepareDuplicateAlerts priorities;
    private final AssessmentDecision decisions;
    private final cd.gouv.finances.signalements.report.validation.AnalysisReplay replay;
    private final cd.gouv.finances.signalements.report.validation.FixedRewards rewards;

    @Value("${app.validation.analysis-enabled:true}")
    private boolean enabled = true;

    // Caller holds the beneficiary lock, shared with import, assessment and payment commands.
    public void execute(UUID reportId) {
        var connected = new HashSet<UUID>();
        connected.add(reportId);
        var pending = new ArrayDeque<UUID>();
        pending.add(reportId);
        while (!pending.isEmpty()) {
            var id = pending.removeFirst();
            var matches = new HashSet<>(attachmentMatches(id, false));
            matches.addAll(invoiceMatches(id));
            for (var match : matches) {
                if (connected.add(match)) pending.add(match);
            }
        }
        confirm(reportId, List.copyOf(connected), false);
    }

    public List<UUID> screenImported(UUID reportId) {
        if (!enabled) return List.of();
        var matches = attachmentMatches(reportId, true);
        var reopened = reopenChangedPhotos(reportId, Set.copyOf(matches));
        confirm(reportId, matches, true);
        return reopened.stream().filter(decisions::previousAutomaticDuplicateWasCleared).toList();
    }

    private void confirm(UUID reportId, List<UUID> group, boolean atImport) {
        if (group.size() < 2 || protectedPayment(group)) return;
        var winner = group.getFirst();
        for (var id : group) {
            if (id.equals(winner)) continue;
            var chosen = priorities.winner(winner, id);
            if (chosen == null) return;
            winner = chosen.id();
        }
        var affected = new HashSet<UUID>();
        var preclassified = new HashSet<UUID>();
        for (var id : group) {
            if (id.equals(winner)) continue;
            var left = winner.compareTo(id) < 0 ? winner : id;
            var right = winner.compareTo(id) < 0 ? id : winner;
            // Respect a previous explicit dismissal by an agent.
            var dismissed = jdbc.queryForObject("""
                    select exists(select 1 from app.duplicate_alert
                    where report_id = ? and other_report_id = ? and status = 'dismissed')
                    """, Boolean.class, left, right);
            if (Boolean.TRUE.equals(dismissed)) continue;
            var confirmed = confirmed(left, right, winner);
            if (!confirmed) {
                var criterion = atImport ? "photo" : "invoice";
                var explanation = atImport ? "Les deux signalements contiennent les mêmes pièces." : "Même facture.";
                jdbc.update("""
                        insert into app.duplicate_alert
                          (id,report_id,other_report_id,criterion,explanation,strength,status,created_at)
                        values (?,?,?,?,?,'likely','open',current_timestamp)
                        on conflict do nothing
                        """, UUID.randomUUID(), left, right, criterion, explanation);
                var changed = jdbc.update("""
                        update app.duplicate_alert set status='confirmed', reason=?,
                          actor_id=?, priority_report_id=?, decided_at=current_timestamp
                        where report_id=? and other_report_id=? and (not ? or criterion='photo')
                          and (status='open' or (actor_id=? and status='confirmed' and priority_report_id is distinct from ?))
                        """, atImport ? "Pièces identiques vérifiées à l’import." : "Même facture : rapprochement automatique.",
                        ValidationRules.AUTOMATION, winner, left, right, atImport, ValidationRules.AUTOMATION, winner);
                if (changed > 0) {
                    confirmed = true;
                    affected.add(id);
                    affected.add(winner);
                }
            }
            if (atImport && confirmed) {
                var finished = jdbc.update("""
                        update app.report_assessment set job_state='done', lease_id=null, lease_until=null, error_code=null
                        where report_id=? and job_state in ('queued','running')
                        """, id);
                if (finished > 0) {
                    affected.add(id);
                    preclassified.add(id);
                }
            }
        }
        rewards.releaseDuplicateReservations(group);
        // Closed duplicate siblings must not leave the retained dossier waiting on a duplicate check.
        for (var id : affected) {
            if (!atImport && id.equals(reportId)) continue; // The OCR worker completes its own decision.
            if (preclassified.contains(id) || Boolean.TRUE.equals(jdbc.queryForObject(
                    """
                    select exists(select 1 from app.report_assessment a join app.report r on r.id=a.report_id
                    where a.report_id=? and a.job_state='done'
                      and (?::timestamptz is null or a.updated_at>=? or r.imported_at>=?))
                    """, Boolean.class, id, cutoff(), cutoff(), cutoff()))) {
                prepareUniqueness(id);
                decisions.apply(id, "automatic", null);
            }
        }
    }

    private boolean confirmed(UUID left, UUID right, UUID winner) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from app.duplicate_alert where report_id=? and other_report_id=?
                  and status='confirmed' and priority_report_id=?)
                """, Boolean.class, left, right, winner));
    }

    private List<UUID> reopenChangedPhotos(UUID reportId, Set<UUID> matches) {
        var alerts = jdbc.query("""
                select id, report_id, other_report_id, priority_report_id from app.duplicate_alert
                where (report_id=? or other_report_id=?) and criterion='photo' and status='confirmed' and actor_id=?
                """, (row, n) -> new PhotoMatch(row.getObject(1, UUID.class), row.getObject(2, UUID.class),
                        row.getObject(3, UUID.class), row.getObject(4, UUID.class)),
                reportId, reportId, ValidationRules.AUTOMATION);
        var reopened = new HashSet<UUID>();
        for (var alert : alerts) {
            var other = alert.left().equals(reportId) ? alert.right() : alert.left();
            if (matches.contains(other) || protectedPayment(List.of(reportId, other))) continue;
            jdbc.update("""
                    update app.duplicate_alert set status='open', reason=null, actor_id=null, decided_at=null, priority_report_id=null,
                      explanation='Les pièces ont changé depuis le rapprochement automatique.'
                    where id=?
                    """, alert.id());
            reopened.add(alert.loser());
        }
        return List.copyOf(reopened);
    }

    private record PhotoMatch(UUID id, UUID left, UUID right, UUID winner) {
        UUID loser() { return winner.equals(left) ? right : left; }
    }

    private List<UUID> attachmentMatches(UUID reportId, boolean atImport) {
        return jdbc.query("""
                with candidates as (
                  select distinct other.report_id
                  from app.report_photo mine join app.report_photo other on other.fingerprint=mine.fingerprint
                  where mine.report_id=?
                ), attachments as (
                  select p.report_id, array_agg(distinct p.fingerprint order by p.fingerprint) as fingerprints
                  from candidates c join app.report_photo p on p.report_id=c.report_id join app.report r on r.id=p.report_id
                  where r.validation_state <> 'legacy'
                    and (r.source_photos_complete=true or (not ? and r.source_photos_complete is null))
                  group by p.report_id having bool_and(p.fingerprint is not null)
                )
                select other.report_id from attachments mine join attachments other
                  on mine.fingerprints=other.fingerprints where mine.report_id=?
                """, (row, n) -> row.getObject(1, UUID.class), reportId, atImport, reportId);
    }

    private List<UUID> invoiceMatches(UUID reportId) {
        return jdbc.query("""
                with invoices as (
                  select a.report_id,
                    lower(trim(a.seller_tax_id)) as nif,
                    lower(trim(a.document_number)) as number,
                    a.document_date::text as issued,
                    case when trim(a.extracted->>'totalAmount') ~ '^[0-9]+([.,][0-9]{1,2})?$'
                      then replace(trim(a.extracted->>'totalAmount'),',','.')::numeric end as amount,
                    upper(trim(a.extracted->>'currency')) as currency
                  from app.report_assessment a join app.report r on r.id=a.report_id
                  join lateral (select d.status,d.quality from app.document_reading d
                    where d.document_reference=cast(a.photo_id as text)
                    order by d.created_at desc,d.id desc limit 1) reading on true
                  where a.job_state='done' and r.validation_state<>'legacy'
                    and reading.status='READ' and reading.quality='READABLE'
                )
                select other.report_id from invoices mine join invoices other
                  on mine.nif=other.nif and mine.number=other.number and mine.issued=other.issued
                    and mine.amount=other.amount and mine.currency=other.currency
                  where mine.report_id=? and mine.nif<>'' and mine.number<>'' and mine.issued<>''
                    and mine.amount is not null and mine.currency ~ '^[A-Z]{3}$'
                    and mine.issued ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
                    and mine.nif not in ('n/a','na','inconnu','non lu','aucun','null','none','-')
                    and mine.number not in ('n/a','na','inconnu','non lu','aucun','null','none','-')
                """, (row, n) -> row.getObject(1, UUID.class), reportId);
    }

    private java.sql.Timestamp cutoff() { return replay.before() == null ? null : java.sql.Timestamp.from(replay.before()); }

    public void prepareUniqueness(UUID reportId) {
        jdbc.update("""
                update app.report_assessment a set unique_invoice='unique',
                  uniqueness_evidence='Recherche automatique des signalements antérieurs.'
                where a.report_id=? and a.unique_invoice='unknown' and a.document_number is not null
                  and (nullif(trim(a.seller_name),'') is not null or nullif(trim(a.seller_tax_id),'') is not null)
                    and not exists(select 1 from app.duplicate_alert d where (d.report_id=a.report_id or d.other_report_id=a.report_id)
                    and ((d.status='open' and not exists (select 1 from app.duplicate_alert confirmed
                        where confirmed.report_id=d.report_id and confirmed.other_report_id=d.other_report_id and confirmed.status='confirmed'))
                      or (d.status='confirmed' and d.priority_report_id<>a.report_id)))
                """, reportId);
    }

    private boolean protectedPayment(List<UUID> ids) {
        var marks = String.join(",", ids.stream().map(id -> "?").toList());
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from app.prime p where p.report_id in (%s) and (
                  exists(select 1 from app.payment_statement_line l join app.payment_attempt a on a.line_id=l.id
                    where l.prime_id=p.id and a.result='paid' and a.reference_code not like 'SIM-%%')
                  or exists(select 1 from app.payment_statement_line l join app.payment_transfer t on t.line_id=l.id
                    where l.prime_id=p.id and t.environment='production' and t.state in ('submitting','pending','unknown','paid'))))
                """.formatted(marks), Boolean.class, ids.toArray()));
    }
}
