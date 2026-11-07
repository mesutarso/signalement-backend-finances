package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "abuse_note")
@Getter
public class AbuseNote {

    public static final String NOTED = "noted";
    public static final String DISMISSED = "dismissed";

    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(nullable = false, length = 16)
    private String decision;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected AbuseNote() {
    }

    public AbuseNote(UUID id, String fingerprint, String decision, String reason, UUID actorId, Instant recordedAt) {
        this.id = id;
        this.fingerprint = fingerprint;
        this.decision = decision;
        this.reason = reason;
        this.actorId = actorId;
        this.recordedAt = recordedAt;
    }
}
