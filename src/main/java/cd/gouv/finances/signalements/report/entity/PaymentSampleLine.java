package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "payment_sample_line")
@Getter
public class PaymentSampleLine {

    public static final String CONFORM = "conform";
    public static final String ANOMALY = "anomaly";

    @Id
    private UUID id;

    @Column(name = "sample_id", nullable = false)
    private UUID sampleId;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(nullable = false, length = 80)
    private String referenceCode;

    @Column(nullable = false, length = 16)
    private String operator;

    @Column(nullable = false)
    private int amount;

    @Column(length = 16)
    private String result;

    @Column(length = 500)
    private String reason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected PaymentSampleLine() {
    }

    public PaymentSampleLine(UUID id, UUID sampleId, UUID attemptId, String referenceCode, String operator, int amount) {
        this.id = id;
        this.sampleId = sampleId;
        this.attemptId = attemptId;
        this.referenceCode = referenceCode;
        this.operator = operator;
        this.amount = amount;
    }

    public void review(String result, String reason, UUID actorId, Instant at) {
        this.result = result;
        this.reason = reason;
        this.reviewedBy = actorId;
        this.reviewedAt = at;
    }
}
