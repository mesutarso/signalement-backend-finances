package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "payment_attempt")
@Getter
public class PaymentAttempt {

    public static final String PAID = "paid";
    public static final String FAILED = "failed";
    public static final String UNKNOWN = "unknown";

    @Id
    private UUID id;

    @Column(name = "statement_id", nullable = false)
    private UUID statementId;

    @Column(name = "line_id", nullable = false)
    private UUID lineId;

    @Column(nullable = false, length = 16)
    private String operator;

    @Column(nullable = false, length = 16)
    private String result;

    @Column(name = "reference_code", length = 80)
    private String referenceCode;

    @Column(length = 500)
    private String reason;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected PaymentAttempt() {
    }

    public PaymentAttempt(UUID id, UUID statementId, UUID lineId, String operator, String result, String referenceCode, String reason, UUID actorId, Instant recordedAt) {
        this.id = id;
        this.statementId = statementId;
        this.lineId = lineId;
        this.operator = operator;
        this.result = result;
        this.referenceCode = referenceCode;
        this.reason = reason;
        this.actorId = actorId;
        this.recordedAt = recordedAt;
    }
}
