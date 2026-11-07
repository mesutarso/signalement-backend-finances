package cd.gouv.finances.signalements.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "audit_entry")
@Getter
public class AuditEntry {

    @Id
    private UUID id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(name = "object_type", nullable = false, length = 32)
    private String objectType;

    @Column(name = "object_id", length = 80)
    private String objectId;

    @Column(nullable = false, length = 16)
    private String result;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected AuditEntry() {
    }

    public AuditEntry(
            UUID id,
            UUID actorId,
            String action,
            String objectType,
            String objectId,
            String result,
            Instant recordedAt) {
        this.id = id;
        this.actorId = actorId;
        this.action = action;
        this.objectType = objectType;
        this.objectId = objectId;
        this.result = result;
        this.recordedAt = recordedAt;
    }
}
