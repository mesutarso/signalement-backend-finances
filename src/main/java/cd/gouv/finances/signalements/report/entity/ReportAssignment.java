package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "report_assignment")
@Getter
public class ReportAssignment {

    @Id
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "previous_assignee_id")
    private UUID previousAssigneeId;

    @Column(length = 16)
    private String priority;

    @Column(name = "previous_priority", length = 16)
    private String previousPriority;

    @Column(nullable = false, length = 32)
    private String state;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected ReportAssignment() {
    }

    public ReportAssignment(
            UUID id,
            UUID reportId,
            UUID actorId,
            UUID assigneeId,
            UUID previousAssigneeId,
            String priority,
            String previousPriority,
            String state,
            Instant recordedAt) {
        this.id = id;
        this.reportId = reportId;
        this.actorId = actorId;
        this.assigneeId = assigneeId;
        this.previousAssigneeId = previousAssigneeId;
        this.priority = priority;
        this.previousPriority = previousPriority;
        this.state = state;
        this.recordedAt = recordedAt;
    }
}
