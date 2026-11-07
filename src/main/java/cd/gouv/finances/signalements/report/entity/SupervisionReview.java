package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "supervision_review")
@Getter
public class SupervisionReview {

    public static final String UPHELD = "upheld";
    public static final String RETURNED = "returned";

    @Id
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "qualification_id", nullable = false)
    private UUID qualificationId;

    @Column(nullable = false, length = 16)
    private String result;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected SupervisionReview() {
    }

    public SupervisionReview(
            UUID id,
            UUID reportId,
            UUID qualificationId,
            String result,
            String reason,
            UUID actorId,
            Instant recordedAt) {
        this.id = id;
        this.reportId = reportId;
        this.qualificationId = qualificationId;
        this.result = result;
        this.reason = reason;
        this.actorId = actorId;
        this.recordedAt = recordedAt;
    }
}
