package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "letter")
@Getter
public class Letter {

    public static final String DRAFT = "draft";
    public static final String VALIDATED = "validated";
    public static final String RETURNED = "returned";

    @Id
    private UUID id;

    @Column(name = "reference_code", nullable = false, length = 32)
    private String referenceCode;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 160)
    private String recipient;

    @Column(name = "service_name", nullable = false, length = 160)
    private String serviceName = "";

    @Column(length = 500)
    private String reason;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "prepared_at", nullable = false)
    private Instant preparedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    protected Letter() {
    }

    public Letter(UUID id, String referenceCode, LocalDate periodStart, LocalDate periodEnd, UUID actorId, Instant preparedAt) {
        this.id = id;
        this.referenceCode = referenceCode;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.status = DRAFT;
        this.actorId = actorId;
        this.preparedAt = preparedAt;
    }

    public void decide(String status, String recipient, String reason, UUID actorId, Instant decidedAt) {
        this.status = status;
        this.recipient = recipient;
        this.reason = reason;
        this.decidedBy = actorId;
        this.decidedAt = decidedAt;
    }
}
