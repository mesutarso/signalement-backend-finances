package cd.gouv.finances.signalements.report.validation;

import cd.gouv.finances.signalements.report.ReportRejected;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AssessmentEvidence {
    private final JdbcTemplate jdbc;

    public record Photo(UUID id, String contentType) { }
    public record Reading(UUID photoId, UUID readingId, String status, String model, String errorCode,
                          Map<String, String> fields) { }

    public List<Photo> photos(UUID reportId) {
        return jdbc.query("select id, content_type from app.report_photo where report_id = ? order by external_id, id",
                (r, n) -> new Photo(r.getObject(1, UUID.class), r.getString(2)), reportId);
    }

    public byte[] content(UUID photoId) {
        return jdbc.queryForObject("select content from app.report_photo where id = ?", byte[].class, photoId);
    }

    public List<Reading> readings(UUID reportId) {
        return photos(reportId).stream().map(photo -> latest(photo.id())).filter(r -> r != null).toList();
    }

    public Reading latest(UUID photoId) {
        var rows = jdbc.query("""
                select id, status, model, error_code from app.document_reading where document_reference = ?
                order by created_at desc, id desc limit 1
                """, (r, n) -> new Reading(photoId, r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getString(4), Map.of()), photoId.toString());
        if (rows.isEmpty()) return null;
        var reading = rows.getFirst();
        var fields = new LinkedHashMap<String, String>();
        jdbc.query("select name, value from app.extracted_field where reading_id = ? and present", r -> {
            if (r.getString(2) != null) fields.put(r.getString(1), r.getString(2));
        }, reading.readingId());
        return new Reading(photoId, reading.readingId(), reading.status(), reading.model(), reading.errorCode(), Map.copyOf(fields));
    }

    public UUID selected(UUID reportId, UUID requested) {
        var photos = photos(reportId);
        if (requested != null && photos.stream().noneMatch(p -> p.id().equals(requested))) {
            throw new ReportRejected("assessment-photo", 400);
        }
        return requested != null ? requested : photos.size() == 1 ? photos.getFirst().id() : null;
    }

    public String duplicateStatus(UUID reportId) {
        Boolean losing = jdbc.queryForObject("""
                select exists (select 1 from app.duplicate_alert where (report_id = ? or other_report_id = ?)
                  and status = 'confirmed' and priority_report_id <> ?)
                """, Boolean.class, reportId, reportId, reportId);
        return Boolean.TRUE.equals(losing) ? "duplicate" : "unknown";
    }

    public boolean unresolvedDuplicates(UUID reportId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from app.duplicate_alert where (report_id = ? or other_report_id = ?) and status = 'open')
                """, Boolean.class, reportId, reportId));
    }

    public static String charged(String value) {
        if (value == null) return "unknown";
        var text = value.trim().replace("\u00a0", "").replace(" ", "");
        if (!text.matches("[0-9]+([.,][0-9]{1,2})?")) return "unknown";
        return new BigDecimal(text.replace(',', '.')).signum() > 0 ? "yes" : "no";
    }
}
