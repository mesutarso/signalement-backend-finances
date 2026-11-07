package cd.gouv.finances.signalements.report.duplicate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DuplicateLookup {

    public record PhotoPair(UUID otherReportId) {
    }

    public record FieldRow(UUID reportId, String name, String value) {
    }

    public record Timing(
            UUID id,
            String identifier,
            LocalDateTime reportedAt,
            String referenceCode) {
    }

    private final JdbcTemplate jdbc;

    public DuplicateLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PhotoPair> photoPairs(UUID reportId) {
        return jdbc.query(
                """
                select distinct other.report_id
                from app.report_photo mine
                join app.report_photo other
                  on other.fingerprint = mine.fingerprint
                 and other.report_id <> mine.report_id
                where mine.report_id = ?
                  and mine.fingerprint is not null
                """,
                (row, number) -> new PhotoPair(row.getObject(1, UUID.class)),
                reportId);
    }

    public List<FieldRow> readings() {
        return jdbc.query(
                """
                select p.report_id, f.name, f.value
                from app.report_photo p
                join app.document_reading d
                  on d.document_reference = cast(p.id as text)
                 and d.status = 'READ'
                join app.extracted_field f
                  on f.reading_id = d.id
                 and f.present
                where f.name in ('documentNumber', 'sellerTaxId', 'readCode')
                """,
                (row, number) -> new FieldRow(row.getObject(1, UUID.class), row.getString(2), row.getString(3)));
    }

    public List<Timing> timings(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        var marks = String.join(",", ids.stream().map(id -> "?").toList());
        return jdbc.query(
                """
                select id, external_id, reported_at, reference_code
                from app.report
                where id in (%s)
                """.formatted(marks),
                (row, number) -> new Timing(
                        row.getObject(1, UUID.class),
                        row.getString(2),
                        row.getTimestamp(3).toLocalDateTime(),
                        row.getString(4)),
                ids.toArray());
    }

    public List<UUID> completeReports(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        var marks = String.join(",", ids.stream().map(id -> "?").toList());
        return jdbc.query(
                """
                select distinct p.report_id
                from app.report_photo p
                join app.document_reading d
                  on d.document_reference = cast(p.id as text)
                where p.report_id in (%s)
                  and d.status = 'READ'
                  and d.quality = 'READABLE'
                """.formatted(marks),
                (row, number) -> row.getObject(1, UUID.class),
                ids.toArray());
    }
}
