package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "prime")
@Getter
public class Prime {

    public static final String PROPOSED = "proposed";
    public static final String APPROVED = "approved";
    public static final String RETURNED = "returned";
    public static final String WITHDRAWN = "withdrawn";

    @Id
    private UUID id;

    @Column(name = "declarant_id", nullable = false, length = 80)
    private String declarantId;

    @Column(length = 40)
    private String phone;

    @Column(name = "rule_version", nullable = false, length = 32)
    private String ruleVersion;

    @Column(nullable = false)
    private int threshold;

    @Column(name = "report_id")
    private UUID reportId;

    @Column(name = "reward_setting_id")
    private UUID rewardSettingId;

    @Column(name = "invoice_key", length = 64)
    private String invoiceKey;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Prime() {
    }

    public Prime(
            UUID id,
            String declarantId,
            String phone,
            String ruleVersion,
            int threshold,
            int amount,
            UUID actorId,
            Instant recordedAt) {
        this.id = id;
        this.declarantId = declarantId;
        this.phone = phone;
        this.ruleVersion = ruleVersion;
        this.threshold = threshold;
        this.amount = amount;
        this.status = PROPOSED;
        this.actorId = actorId;
        this.recordedAt = recordedAt;
        this.updatedAt = recordedAt;
    }

    public void propose(String phone, UUID actorId, Instant updatedAt) {
        this.phone = phone;
        this.status = PROPOSED;
        this.actorId = actorId;
        this.updatedAt = updatedAt;
    }

    public void withdraw(UUID actorId, Instant updatedAt) {
        this.status = WITHDRAWN;
        this.actorId = actorId;
        this.updatedAt = updatedAt;
    }

    public void approve(UUID actorId, Instant updatedAt) {
        this.status = APPROVED;
        this.actorId = actorId;
        this.updatedAt = updatedAt;
    }

    public void returnForCorrection(UUID actorId, Instant updatedAt) {
        this.status = RETURNED;
        this.actorId = actorId;
        this.updatedAt = updatedAt;
    }
}
