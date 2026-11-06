package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "report_photo")
@Getter
public class ReportPhoto {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @Column(name = "external_id", nullable = false, length = 80)
    private String externalId;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(nullable = false)
    private byte[] content;

    protected ReportPhoto() {
    }

    public ReportPhoto(UUID id, Report report, String externalId, String contentType, byte[] content) {
        this.id = id;
        this.report = report;
        this.externalId = externalId;
        this.contentType = contentType;
        this.content = content.clone();
    }
}
