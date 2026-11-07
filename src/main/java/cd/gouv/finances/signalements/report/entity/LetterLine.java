package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "letter_line")
@Getter
public class LetterLine {

    public static final String COMPANY = "company";
    public static final String INVOICE = "invoice";
    public static final String SHEET = "sheet";
    public static final String PIECE = "piece";

    @Id
    private UUID id;

    @Column(name = "letter_id", nullable = false)
    private UUID letterId;

    @Column(nullable = false, length = 16)
    private String annex;

    @Column(nullable = false)
    private int position;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "report_reference", nullable = false, length = 80)
    private String reportReference;

    @Column(name = "tax_id", length = 40)
    private String taxId;

    @Column(name = "seller_name", length = 160)
    private String sellerName;

    @Column(length = 16)
    private String decision;

    @Column(length = 32)
    private String finding;

    @Column(length = 16)
    private String proof;

    @Column(name = "document_number", length = 80)
    private String documentNumber;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    @Column(length = 40)
    private String amount;

    @Column(length = 500)
    private String justification;

    @Column(name = "missing_elements", length = 500)
    private String missingElements;

    @Column(name = "dgi_reference", length = 80)
    private String dgiReference;

    @Column(name = "photo_id")
    private UUID photoId;

    @Column(name = "content_type", length = 40)
    private String contentType;

    protected LetterLine() {
    }

    public LetterLine(
            UUID id,
            UUID letterId,
            String annex,
            int position,
            UUID reportId,
            String reportReference,
            String taxId,
            String sellerName,
            String decision,
            String finding,
            String proof,
            String documentNumber,
            LocalDate issuedOn,
            String amount,
            String justification,
            String missingElements,
            String dgiReference,
            UUID photoId,
            String contentType) {
        this.id = id;
        this.letterId = letterId;
        this.annex = annex;
        this.position = position;
        this.reportId = reportId;
        this.reportReference = reportReference;
        this.taxId = taxId;
        this.sellerName = sellerName;
        this.decision = decision;
        this.finding = finding;
        this.proof = proof;
        this.documentNumber = documentNumber;
        this.issuedOn = issuedOn;
        this.amount = amount;
        this.justification = justification;
        this.missingElements = missingElements;
        this.dgiReference = dgiReference;
        this.photoId = photoId;
        this.contentType = contentType;
    }
}
