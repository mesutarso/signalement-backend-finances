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
    private static final List<String> NORMALIZATION_FIELDS = List.of("readCode", "defNumber", "defCode");
    private final JdbcTemplate jdbc;

    public record Photo(UUID id, String contentType) { }
    public record Reading(UUID photoId, UUID readingId, String status, String quality, String model, String errorCode,
                          Map<String, String> fields, Instant createdAt) { }

    public List<Photo> photos(UUID reportId) {
        return jdbc.query("select id, content_type from app.report_photo where report_id = ? order by (external_id like 'NP-%') desc, external_id, id",
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
            if (reading == null || !"READ".equals(reading.status())) return "UNKNOWN";
            if (!"READABLE".equals(reading.quality())
                    && ("yes".equals(charged(reading.fields().get("vatAmount"))) || NORMALIZATION_FIELDS.stream().anyMatch(field -> !blank(reading.fields().get(field))))) {
                return "PARTIAL";
            }
            return reading.quality() == null ? "UNKNOWN" : reading.quality();
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
        // Prefer a photo containing a usable VAT amount, then the most complete reading.
        var replacements = jdbc.query("""
                select id from app.report_photo where report_id=? and replacement_request_id=(
                  select id from app.photo_request where report_id=? and status='linked'
                  order by resolved_at desc,id desc limit 1) order by external_id,id
                """,
                (r,n)->r.getObject(1,UUID.class),reportId,reportId);
        var candidates = replacements.isEmpty() ? photos : photos.stream().filter(p->replacements.contains(p.id())).toList();
        var chosen = candidates.stream().map(photo -> latest(photo.id()))
                .filter(reading -> reading != null && "READ".equals(reading.status()))
                .min(java.util.Comparator.comparingInt(AssessmentEvidence::readingPriority))
                .map(Reading::photoId).orElse(null);
        return chosen != null ? chosen : candidates.isEmpty() ? null : candidates.getFirst().id();
    }

    private static int readingPriority(Reading reading) {
        var vat = "yes".equals(charged(reading.fields().get("vatAmount")));
        if ("READABLE".equals(reading.quality())) return vat ? 0 : 2;
        if (vat) return 1;
        if ("PARTIAL".equals(reading.quality())) return blank(reading.fields().get("sellerTaxId")) ? 4 : 3;
        return 5;
    }

    public static boolean hasCompleteNormalization(Map<String, String> fields) {
        return NORMALIZATION_FIELDS.stream().allMatch(field -> !blank(fields.get(field)));
    }

    static boolean hasCompleteNormalization(AssessmentStore.Snapshot assessment) {
        return hasCompleteNormalization(assessment.extracted())
                || assessment.readings().stream().anyMatch(reading -> "READ".equals(reading.status())
                        && hasCompleteNormalization(reading.fields()));
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
