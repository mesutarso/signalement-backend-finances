package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "duplicate_alert")
@Getter
public class DuplicateAlert {

    public static final String OPEN = "open";
    public static final String CONFIRMED = "confirmed";
    public static final String DISMISSED = "dismissed";
    public static final String PHOTO = "photo";

    @Id
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "other_report_id", nullable = false)
    private UUID otherReportId;

    @Column(nullable = false, length = 32)
    private String criterion;

    @Column(nullable = false, length = 400)
    private String explanation;

    @Column(nullable = false, length = 16)
    private String strength;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 500)
    private String reason;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "priority_report_id")
    private UUID priorityReportId;

    protected DuplicateAlert() {
    }

    public DuplicateAlert(
            UUID id,
            UUID reportId,
            UUID otherReportId,
            String criterion,
            String explanation,
            String strength,
            Instant createdAt) {
        this.id = id;
        this.reportId = reportId;
        this.otherReportId = otherReportId;
        this.criterion = criterion;
        this.explanation = explanation;
        this.strength = strength;
        this.status = OPEN;
        this.createdAt = createdAt;
    }

    public void confirm(String reason, UUID actorId, UUID priorityReportId, Instant decidedAt) {
        this.status = CONFIRMED;
        this.reason = reason;
        this.actorId = actorId;
        this.priorityReportId = priorityReportId;
        this.decidedAt = decidedAt;
    }

    public void dismiss(String reason, UUID actorId, Instant decidedAt) {
        this.status = DISMISSED;
        this.reason = reason;
        this.actorId = actorId;
        this.priorityReportId = null;
        this.decidedAt = decidedAt;
    }
}
