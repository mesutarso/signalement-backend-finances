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
@Table(name = "payment_statement")
@Getter
public class PaymentStatement {

    public static final String DRAFT = "draft";
    public static final String CONTROLLED = "controlled";
    public static final String VISAED = "visaed";
    public static final String AUTHORIZED = "authorized";
    public static final String RETURNED = "returned";
    public static final String REFUSED = "refused";

    @Id
    private UUID id;

    @Column(name = "reference_code", nullable = false, length = 32)
    private String referenceCode;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "prepared_by", nullable = false)
    private UUID preparedBy;

    @Column(name = "prepared_at", nullable = false)
    private Instant preparedAt;

    @Column(name = "controlled_by")
    private UUID controlledBy;

    @Column(name = "controlled_at")
    private Instant controlledAt;

    @Column(name = "visaed_by")
    private UUID visaedBy;

    @Column(name = "visaed_at")
    private Instant visaedAt;

    @Column(name = "authorized_by")
    private UUID authorizedBy;

    @Column(name = "authorized_at")
    private Instant authorizedAt;

    @Column(name = "due_on")
    private LocalDate dueOn;

    @Column(length = 500)
    private String reason;

    protected PaymentStatement() {
    }

    public PaymentStatement(UUID id, String referenceCode, UUID preparedBy, Instant preparedAt) {
        this.id = id;
        this.referenceCode = referenceCode;
        this.status = DRAFT;
        this.preparedBy = preparedBy;
        this.preparedAt = preparedAt;
    }

    public void control(UUID actorId, Instant at) {
        this.status = CONTROLLED;
        this.controlledBy = actorId;
        this.controlledAt = at;
    }

    public void visa(UUID actorId, Instant at) {
        this.status = VISAED;
        this.visaedBy = actorId;
        this.visaedAt = at;
    }

    public void authorize(UUID actorId, Instant at, LocalDate dueOn) {
        this.status = AUTHORIZED;
        this.authorizedBy = actorId;
        this.authorizedAt = at;
        this.dueOn = dueOn;
    }

    public void returnForCorrection(String reason) {
        this.status = RETURNED;
        this.reason = reason;
    }

    public void refuse(String reason) {
        this.status = REFUSED;
        this.reason = reason;
    }
}
