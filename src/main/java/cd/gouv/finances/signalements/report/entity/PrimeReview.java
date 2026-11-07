package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "prime_review")
@Getter
public class PrimeReview {

    public static final String APPROVED = "approved";
    public static final String RETURNED = "returned";

    @Id
    private UUID id;

    @Column(name = "prime_id", nullable = false)
    private UUID primeId;

    @Column(nullable = false, length = 16)
    private String result;

    @Column(length = 500)
    private String reason;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected PrimeReview() {
    }

    public PrimeReview(UUID id, UUID primeId, String result, String reason, UUID actorId, Instant recordedAt) {
        this.id = id;
        this.primeId = primeId;
        this.result = result;
        this.reason = reason;
        this.actorId = actorId;
        this.recordedAt = recordedAt;
    }
}
