package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "qualification")
@Getter
public class Qualification {

    public static final String CONFIRM = "confirm";
    public static final String REJECT = "reject";

    @Id
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(nullable = false, length = 16)
    private String decision;

    @Column(length = 32)
    private String finding;

    @Column(length = 16)
    private String proof;

    @Column(nullable = false, length = 500)
    private String justification;

    @Column(name = "seller_name", length = 160)
    private String sellerName;

    @Column(name = "seller_tax_id", length = 40)
    private String sellerTaxId;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    @Column(length = 40)
    private String amount;

    @Column(name = "document_number", length = 80)
    private String documentNumber;

    @Column(name = "read_code", length = 80)
    private String code;

    @Column(name = "missing_elements", length = 500)
    private String missingElements;

    @Column(name = "dgi_reference", length = 80)
    private String dgiReference;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected Qualification() {
    }

    public Qualification(
            UUID id,
            UUID reportId,
            String decision,
            String finding,
            String proof,
            String justification,
            String sellerName,
            String sellerTaxId,
            LocalDate issuedOn,
            String amount,
            String documentNumber,
            String code,
            String missingElements,
            String dgiReference,
            UUID actorId,
            Instant recordedAt) {
        this.id = id;
        this.reportId = reportId;
        this.decision = decision;
        this.finding = finding;
        this.proof = proof;
        this.justification = justification;
        this.sellerName = sellerName;
        this.sellerTaxId = sellerTaxId;
        this.issuedOn = issuedOn;
        this.amount = amount;
        this.documentNumber = documentNumber;
        this.code = code;
        this.missingElements = missingElements;
        this.dgiReference = dgiReference;
        this.actorId = actorId;
        this.recordedAt = recordedAt;
    }

    public boolean same(
            String decision,
            String finding,
            String proof,
            String justification,
            String sellerName,
            String sellerTaxId,
            LocalDate issuedOn,
            String amount,
            String documentNumber,
            String code,
            String missingElements,
            String dgiReference) {
        return Objects.equals(this.decision, decision)
                && Objects.equals(this.finding, finding)
                && Objects.equals(this.proof, proof)
                && Objects.equals(this.justification, justification)
                && Objects.equals(this.sellerName, sellerName)
                && Objects.equals(this.sellerTaxId, sellerTaxId)
                && Objects.equals(this.issuedOn, issuedOn)
                && Objects.equals(this.amount, amount)
                && Objects.equals(this.documentNumber, documentNumber)
                && Objects.equals(this.code, code)
                && Objects.equals(this.missingElements, missingElements)
                && Objects.equals(this.dgiReference, dgiReference);
    }
}
