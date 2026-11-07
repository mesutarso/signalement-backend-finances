package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "payment_statement_exclusion")
@Getter
public class PaymentStatementExclusion {

    @Id
    private UUID id;

    @Column(name = "statement_id", nullable = false)
    private UUID statementId;

    @Column(name = "prime_id", nullable = false)
    private UUID primeId;

    @Column(nullable = false, length = 160)
    private String reason;

    protected PaymentStatementExclusion() {
    }

    public PaymentStatementExclusion(UUID id, UUID statementId, UUID primeId, String reason) {
        this.id = id;
        this.statementId = statementId;
        this.primeId = primeId;
        this.reason = reason;
    }
}
