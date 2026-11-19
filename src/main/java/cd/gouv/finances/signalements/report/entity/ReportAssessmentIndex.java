package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name="report_assessment")
@Immutable
@Getter
public class ReportAssessmentIndex {
    @Id @Column(name="report_id") private UUID reportId;
    @Column(name="seller_name") private String sellerName;
    @Column(name="seller_tax_id") private String sellerTaxId;
    @Column(name="document_number") private String documentNumber;
    @Column(name="document_date") private LocalDate documentDate;
    @Column(name="vat_status") private String vatStatus;
    @Column(name="job_state") private String jobState;
    @Column(name="error_code") private String errorCode;
    protected ReportAssessmentIndex() { }
}
