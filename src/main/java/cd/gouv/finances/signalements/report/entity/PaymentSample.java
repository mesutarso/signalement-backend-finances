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
@Table(name = "payment_sample")
@Getter
public class PaymentSample {

    @Id
    private UUID id;

    @Column(name = "reference_code", nullable = false, length = 32)
    private String referenceCode;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "prepared_by", nullable = false)
    private UUID preparedBy;

    @Column(name = "prepared_at", nullable = false)
    private Instant preparedAt;

    protected PaymentSample() {
    }

    public PaymentSample(UUID id, String referenceCode, LocalDate periodStart, LocalDate periodEnd, UUID preparedBy, Instant preparedAt) {
        this.id = id;
        this.referenceCode = referenceCode;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.preparedBy = preparedBy;
        this.preparedAt = preparedAt;
    }
}
