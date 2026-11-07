package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "decision_correction")
@Getter
public class DecisionCorrection {

    @Id
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "qualification_id", nullable = false)
    private UUID qualificationId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected DecisionCorrection() {
    }

    public DecisionCorrection(UUID id, UUID reportId, UUID qualificationId, String reason, UUID actorId, Instant recordedAt) {
        this.id = id;
        this.reportId = reportId;
        this.qualificationId = qualificationId;
        this.reason = reason;
        this.actorId = actorId;
        this.recordedAt = recordedAt;
    }
}
