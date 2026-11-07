package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "photo_request")
@Getter
public class PhotoRequest {

    public static final String OPEN = "open";
    public static final String LINKED = "linked";
    public static final String READING = "reading";
    public static final String REVIEW = "review";
    public static final String WAITING = "waiting";

    @Id
    private UUID id;

    @Column(name = "reference_code", nullable = false, length = 32)
    private String referenceCode;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "photo_id", nullable = false)
    private UUID photoId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, length = 16)
    private String basis;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "linked_report_id")
    private UUID linkedReportId;

    @Column(nullable = false, length = 16)
    private String notice;

    protected PhotoRequest() {
    }

    public PhotoRequest(
            UUID id,
            String referenceCode,
            UUID reportId,
            UUID photoId,
            String reason,
            String basis,
            UUID actorId,
            Instant createdAt) {
        this.id = id;
        this.referenceCode = referenceCode;
        this.reportId = reportId;
        this.photoId = photoId;
        this.reason = reason;
        this.basis = basis;
        this.status = OPEN;
        this.actorId = actorId;
        this.createdAt = createdAt;
        this.notice = WAITING;
    }

    public void link(UUID linkedReportId, Instant resolvedAt) {
        this.linkedReportId = linkedReportId;
        this.resolvedAt = resolvedAt;
        this.status = LINKED;
    }
}
