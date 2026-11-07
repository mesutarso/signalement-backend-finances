package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "payout_number")
@Getter
public class PayoutNumber {

    @Id
    private UUID id;

    @Column(name = "declarant_id", nullable = false, length = 80)
    private String declarantId;

    @Column(length = 40)
    private String phone;

    @Column(nullable = false, length = 16)
    private String state;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PayoutNumber() {
    }

    public PayoutNumber(UUID id, String declarantId, String phone, String state, UUID actorId, Instant recordedAt) {
        this.id = id;
        this.declarantId = declarantId;
        this.phone = phone;
        this.state = state;
        this.actorId = actorId;
        this.recordedAt = recordedAt;
        this.updatedAt = recordedAt;
    }

    public boolean record(String phone, String state, UUID actorId, Instant updatedAt) {
        if (Objects.equals(this.phone, phone) && this.state.equals(state)) {
            return false;
        }
        this.phone = phone;
        this.state = state;
        this.actorId = actorId;
        this.updatedAt = updatedAt;
        return true;
    }
}
