package cd.gouv.finances.signalements.report.abuse;

import cd.gouv.finances.signalements.report.dto.AbuseIndexPageResponse;
import cd.gouv.finances.signalements.report.dto.AbuseIndexResponse;
import cd.gouv.finances.signalements.report.dto.AccountIndexResponse;
import cd.gouv.finances.signalements.report.dto.SeriesIndexResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReadAbuseIndicesQuery {

    static final String EXPLANATION = "La même image est jointe à plusieurs signalements. Cet indice n'est pas une preuve de fraude.";
    static final String SERIES = "Plus de cinq signalements ont été déposés le même jour. Cet indice n'est pas une preuve de fraude.";
    static final String ACCOUNTS = "Plusieurs comptes portent le même numéro. Les comptes ne sont pas fusionnés. Cet indice n'est pas une preuve de fraude.";
    static final String RETOUCH = "La détection des photos retouchées n'est pas disponible.";
    static final int SCORE_START = 100;
    static final int SCORE_STEP = 20;
    private static final int SIZE = 20;

    private final JdbcTemplate jdbc;

    public ReadAbuseIndicesQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public AbuseIndexPageResponse execute(int page) {
        var requested = Math.max(page, 0);
        var total = jdbc.queryForObject(
                """
                select count(*) from (
                    select fingerprint
                    from app.report_photo
                    where fingerprint is not null
                    group by fingerprint
                    having count(distinct report_id) > 1
                ) shared
                """,
                Long.class);
        var fingerprints = jdbc.query(
                """
                select fingerprint, count(distinct report_id)
                from app.report_photo
                where fingerprint is not null
                group by fingerprint
                having count(distinct report_id) > 1
                order by count(distinct report_id) desc, fingerprint
                limit ? offset ?
                """,
                (row, number) -> new Counted(row.getString(1), row.getInt(2)),
                SIZE,
                requested * SIZE);
        if (fingerprints.isEmpty()) {
            return page(java.util.List.of(), requested, total == null ? 0 : total);
        }
        var marks = String.join(",", fingerprints.stream().map(item -> "?").toList());
        var keys = fingerprints.stream().map(Counted::fingerprint).toArray();
        var reports = new HashMap<String, java.util.List<AbuseIndexResponse.AbuseReportResponse>>();
        jdbc.query(
                """
                select distinct p.fingerprint, r.id, r.external_id, r.reported_at
                from app.report_photo p
                join app.report r on r.id = p.report_id
                where p.fingerprint in (%s)
                order by r.reported_at
                """.formatted(marks),
                (row, number) -> {
                    reports.computeIfAbsent(row.getString(1), key -> new ArrayList<>())
                            .add(new AbuseIndexResponse.AbuseReportResponse(
                                    row.getObject(2, UUID.class),
                                    row.getString(3),
                                    row.getTimestamp(4).toLocalDateTime()));
                    return null;
                },
                keys);
        var notes = new HashMap<String, Note>();
        jdbc.query(
                """
                select distinct on (fingerprint) fingerprint, decision, reason, actor_id, recorded_at
                from app.abuse_note
                where fingerprint in (%s)
                order by fingerprint, recorded_at desc
                """.formatted(marks),
                (row, number) -> notes.put(row.getString(1), new Note(
                        row.getString(2),
                        row.getString(3),
                        row.getObject(4, UUID.class),
                        row.getTimestamp(5).toInstant())),
                keys);
        var actorIds = notes.values().stream().map(Note::actorId).distinct().toList();
        var names = names(actorIds);
        var indices = fingerprints.stream().map(item -> {
            var note = notes.get(item.fingerprint());
            return new AbuseIndexResponse(
                    item.fingerprint(),
                    item.count(),
                    reports.getOrDefault(item.fingerprint(), java.util.List.of()),
                    EXPLANATION,
                    note == null ? "open" : note.decision(),
                    note == null ? null : note.reason(),
                    note == null ? null : names.get(note.actorId()),
                    note == null ? null : note.recordedAt());
        }).toList();
        return page(indices, requested, total == null ? 0 : total);
    }

    private AbuseIndexPageResponse page(java.util.List<AbuseIndexResponse> indices, int requested, long total) {
        var series = series();
        var accounts = accounts();
        var score = SCORE_START;
        if (!indices.isEmpty()) {
            score -= SCORE_STEP;
        }
        if (!series.isEmpty()) {
            score -= SCORE_STEP;
        }
        if (!accounts.isEmpty()) {
            score -= SCORE_STEP;
        }
        var text = "Score de fiabilité : " + score
                + ". Départ 100, moins 20 pour chaque famille d'indice présente sur cette page. Ce score n'est pas une preuve de fraude.";
        return new AbuseIndexPageResponse(indices, series, accounts, RETOUCH, text, requested, SIZE, total);
    }

    private java.util.List<SeriesIndexResponse> series() {
        return jdbc.query("""
                select reported_on, count(*)::int,
                       (array_agg(id order by reported_at desc))[1:8],
                       (array_agg(reference_code order by reported_at desc))[1:8]
                from app.report
                where reporter_user_id is not null and btrim(reporter_user_id) <> ''
                group by btrim(reporter_user_id), reported_on
                having count(*) > 5
                order by reported_on desc
                limit 20
                """, (row, number) -> new SeriesIndexResponse(
                row.getDate(1).toLocalDate(),
                row.getInt(2),
                indexed(row.getArray(3), row.getArray(4)),
                SERIES));
    }

    private java.util.List<AccountIndexResponse> accounts() {
        return jdbc.query("""
                select count(distinct btrim(reporter_user_id))::int, count(*)::int,
                       (array_agg(id order by reported_at desc))[1:8],
                       (array_agg(reference_code order by reported_at desc))[1:8]
                from app.report
                where reporter_phone is not null and btrim(reporter_phone) <> ''
                  and reporter_user_id is not null and btrim(reporter_user_id) <> ''
                group by btrim(reporter_phone)
                having count(distinct btrim(reporter_user_id)) > 1
                order by 1 desc, 2 desc
                limit 20
                """, (row, number) -> new AccountIndexResponse(
                row.getInt(1),
                row.getInt(2),
                indexed(row.getArray(3), row.getArray(4)),
                ACCOUNTS));
    }

    private java.util.List<SeriesIndexResponse.IndexedReport> indexed(java.sql.Array ids, java.sql.Array references) {
        try {
            if (ids == null || references == null) {
                return java.util.List.of();
            }
            var idValues = (Object[]) ids.getArray();
            var referenceValues = (Object[]) references.getArray();
            var reports = new java.util.ArrayList<SeriesIndexResponse.IndexedReport>();
            for (var index = 0; index < idValues.length; index++) {
                reports.add(new SeriesIndexResponse.IndexedReport(UUID.fromString(idValues[index].toString()), referenceValues[index].toString()));
            }
            return reports;
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private java.util.Map<UUID, String> names(java.util.List<UUID> ids) {
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }
        var marks = String.join(",", ids.stream().map(id -> "?").toList());
        var found = new HashMap<UUID, String>();
        jdbc.query(
                "select id, username from app.user_account where id in (%s)".formatted(marks),
                (row, number) -> found.put(row.getObject(1, UUID.class), row.getString(2)),
                ids.toArray());
        return found;
    }

    private record Counted(String fingerprint, int count) {
    }

    private record Note(String decision, String reason, UUID actorId, Instant recordedAt) {
    }
}
