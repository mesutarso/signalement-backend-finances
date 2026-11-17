package cd.gouv.finances.signalements.report.validation;

import cd.gouv.finances.signalements.report.ReportRejected;
import java.math.BigDecimal;
import java.time.Instant;
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
    public record Reading(UUID photoId, UUID readingId, String status, String quality, String model, String errorCode,
                          Map<String, String> fields, Instant createdAt) { }

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
                select id, status, quality, model, error_code, created_at from app.document_reading where document_reference = ?
                order by created_at desc, id desc limit 1
                """, (r, n) -> new Reading(photoId, r.getObject(1, UUID.class), r.getString(2), r.getString(3), r.getString(4), r.getString(5), Map.of(), r.getTimestamp(6).toInstant()), photoId.toString());
        if (rows.isEmpty()) return null;
        var reading = rows.getFirst();
        var fields = new LinkedHashMap<String, String>();
        jdbc.query("select name, value from app.extracted_field where reading_id = ? and present", r -> {
            if (r.getString(2) != null) fields.put(r.getString(1), r.getString(2));
        }, reading.readingId());
        return new Reading(photoId, reading.readingId(), reading.status(), reading.quality(), reading.model(), reading.errorCode(), Map.copyOf(fields), reading.createdAt());
    }

    public String readability(UUID reportId, UUID selectedPhotoId) {
        if (selectedPhotoId != null) {
            var reading = latest(selectedPhotoId);
            return reading != null && "READ".equals(reading.status()) && reading.quality() != null ? reading.quality() : "UNKNOWN";
        }
        var photos = photos(reportId);
        if (photos.isEmpty()) return "UNKNOWN";
        var readings = photos.stream().map(photo -> latest(photo.id())).toList();
        return readings.stream().allMatch(reading -> reading != null && "READ".equals(reading.status())
                && "UNREADABLE".equals(reading.quality())) ? "UNREADABLE" : "UNKNOWN";
    }

    public UUID selected(UUID reportId, UUID requested) {
        var photos = photos(reportId);
        if (requested != null && photos.stream().noneMatch(p -> p.id().equals(requested))) {
            throw new ReportRejected("assessment-photo", 400);
        }
        if (requested != null) return requested;
        if (photos.size() == 1) return photos.getFirst().id();
        // Choose the first successfully read invoice with a NIF, without asking the agent to select a piece.
        return photos.stream().map(photo -> latest(photo.id()))
                .filter(reading -> reading != null && "READ".equals(reading.status())
                        && !blank(reading.fields().get("sellerTaxId")))
                .map(Reading::photoId).findFirst().orElse(null);
    }

    public boolean taxIdMissingAfterReading(UUID reportId, UUID selectedPhotoId) {
        var ids = selectedPhotoId == null ? photos(reportId).stream().map(Photo::id).toList() : List.of(selectedPhotoId);
        return !ids.isEmpty() && ids.stream().map(this::latest).allMatch(r -> r != null
                && "READ".equals(r.status()) && ("READABLE".equals(r.quality()) || "PARTIAL".equals(r.quality()))
                && blank(r.fields().get("sellerTaxId")));
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

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
