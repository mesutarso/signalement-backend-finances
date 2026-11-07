package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "declarant_message")
@Getter
public class DeclarantMessage {

    public static final String PREPARED = "prepared";

    @Id
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "report_reference", nullable = false, length = 80)
    private String reportReference;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(nullable = false, length = 500)
    private String body;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "prepared_by", nullable = false)
    private UUID preparedBy;

    @Column(name = "prepared_at", nullable = false)
    private Instant preparedAt;

    protected DeclarantMessage() {
    }

    public DeclarantMessage(UUID id, UUID reportId, String reportReference, String kind, String body, UUID preparedBy, Instant preparedAt) {
        this.id = id;
        this.reportId = reportId;
        this.reportReference = reportReference;
        this.kind = kind;
        this.body = body;
        this.status = PREPARED;
        this.preparedBy = preparedBy;
        this.preparedAt = preparedAt;
    }
}
