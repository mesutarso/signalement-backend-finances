package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "reexamination")
@Getter
public class Reexamination {

    public static final String RECEIVED = "received";
    public static final String MAINTAINED = "maintained";
    public static final String MODIFIED = "modified";

    @Id
    private UUID id;

    @Column(name = "reference_code", nullable = false, length = 32)
    private String referenceCode;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "report_reference", nullable = false, length = 80)
    private String reportReference;

    @Column(name = "request_text", nullable = false, length = 500)
    private String requestText;

    @Column(name = "received_on", nullable = false)
    private LocalDate receivedOn;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 500)
    private String comment;

    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected Reexamination() {
    }

    public Reexamination(UUID id, String referenceCode, UUID reportId, String reportReference, String requestText, LocalDate receivedOn, UUID recordedBy, Instant recordedAt) {
        this.id = id;
        this.referenceCode = referenceCode;
        this.reportId = reportId;
        this.reportReference = reportReference;
        this.requestText = requestText;
        this.receivedOn = receivedOn;
        this.status = RECEIVED;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
    }

    public void decide(String status, String comment, UUID actorId, Instant at) {
        this.status = status;
        this.comment = comment;
        this.decidedBy = actorId;
        this.decidedAt = at;
    }
}
