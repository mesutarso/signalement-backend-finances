package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "payment_statement_line")
@Getter
public class PaymentStatementLine {

    @Id
    private UUID id;

    @Column(name = "statement_id", nullable = false)
    private UUID statementId;

    @Column(name = "prime_id", nullable = false)
    private UUID primeId;

    @Column(nullable = false)
    private int threshold;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(nullable = false)
    private boolean released;

    protected PaymentStatementLine() {
    }

    public PaymentStatementLine(UUID id, UUID statementId, UUID primeId, int threshold, int amount, String phone) {
        this.id = id;
        this.statementId = statementId;
        this.primeId = primeId;
        this.threshold = threshold;
        this.amount = amount;
        this.phone = phone;
        this.released = false;
    }

    public void release() {
        this.released = true;
    }
}
