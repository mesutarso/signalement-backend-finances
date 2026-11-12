package cd.gouv.finances.signalements.report.validation;

import cd.gouv.finances.signalements.report.ReportRejected;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class AssessmentStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public record Snapshot(UUID reportId, long revision, String state, String jobState, String reason,
                           UUID photoId, String sellerName, String sellerTaxId, String documentNumber,
                           LocalDate documentDate,
                           String vatStatus, String vatEvidence, String vatSource, UUID checkedBy, Instant checkedAt,
                           String normalization, String normalizationEvidence, String purchaseConformity,
                           String purchaseEvidence, String vatCharged, String vatChargeEvidence,
                           String uniqueness, String uniquenessEvidence, Map<String, String> extracted,
                           List<AssessmentEvidence.Reading> readings,
                           Instant updatedAt) { }
    public record Reward(UUID id, int amount, String status, Instant recordedAt) { }
    public record View(Snapshot assessment, Reward reward, boolean fiscalApiAvailable) { }

    public Snapshot read(UUID reportId) { return read(reportId, false); }
    public Snapshot lock(UUID reportId) { return read(reportId, true); }

    private Snapshot read(UUID reportId, boolean lock) {
        var rows = jdbc.query("""
                select a.*, r.validation_state from app.report_assessment a join app.report r on r.id = a.report_id
                where a.report_id = ?
                """ + (lock ? " for update of a" : ""), (r, n) -> snapshot(r), reportId);
        if (rows.isEmpty()) throw new ReportRejected("assessment-absent", 404);
        return rows.getFirst();
    }

    public View view(UUID reportId) {
        var snapshot = read(reportId);
        var rewards = jdbc.query("select id, amount, status, recorded_at from app.prime where report_id = ?",
                (r, n) -> new Reward(r.getObject(1, UUID.class), r.getInt(2), r.getString(3), r.getTimestamp(4).toInstant()), reportId);
        return new View(snapshot, rewards.isEmpty() ? null : rewards.getFirst(), false);
    }

    public String json(Object value) { return mapper.writeValueAsString(value); }

    private Snapshot snapshot(ResultSet r) throws SQLException {
        return new Snapshot(r.getObject("report_id", UUID.class), r.getLong("revision"), r.getString("validation_state"),
                r.getString("job_state"), r.getString("reason"), r.getObject("photo_id", UUID.class), r.getString("seller_name"),
                r.getString("seller_tax_id"), r.getString("document_number"), r.getObject("document_date", LocalDate.class),
                r.getString("vat_status"), r.getString("vat_evidence"),
                r.getString("vat_source"), r.getObject("checked_by", UUID.class), instant(r, "checked_at"),
                r.getString("normalization"), r.getString("normalization_evidence"), r.getString("purchase_conformity"),
                r.getString("purchase_evidence"), r.getString("vat_charged"), r.getString("vat_charge_evidence"),
                r.getString("unique_invoice"), r.getString("uniqueness_evidence"),
                mapper.readValue(r.getString("extracted"), new TypeReference<Map<String, String>>() { }),
                mapper.readValue(r.getString("reading_ids"), new TypeReference<List<AssessmentEvidence.Reading>>() { }), instant(r, "updated_at"));
    }

    private static Instant instant(ResultSet r, String column) throws SQLException {
        var value = r.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
