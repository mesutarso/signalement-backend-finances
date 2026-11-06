package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "report")
@Getter
public class Report {

    @Id
    private UUID id;

    @Column(name = "external_id", nullable = false, length = 80)
    private String externalId;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "reference_code", nullable = false, length = 80)
    private String referenceCode;

    @Column(name = "reported_on", nullable = false)
    private LocalDate reportedOn;

    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt;

    @Column(length = 200)
    private String title;

    @Column(length = 4000)
    private String description;

    @Column(name = "reporter_name", length = 160)
    private String reporterName;

    @Column(name = "reporter_email", length = 254)
    private String reporterEmail;

    @Column(name = "reporter_phone", length = 40)
    private String reporterPhone;

    @Column(name = "reporter_user_id", length = 80)
    private String reporterUserId;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Column(nullable = false, length = 32)
    private String state = "pending";

    @Column(length = 16)
    private String priority;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("externalId")
    private final List<ReportPhoto> photos = new ArrayList<>();

    protected Report() {
    }

    public Report(
            UUID id,
            String externalId,
            String source,
            String referenceCode,
            LocalDate reportedOn,
            LocalDateTime reportedAt,
            String title,
            String description,
            String reporterName,
            String reporterEmail,
            String reporterPhone,
            String reporterUserId,
            Instant importedAt) {
        this.id = id;
        this.externalId = externalId;
        this.source = source;
        this.referenceCode = referenceCode;
        this.reportedOn = reportedOn;
        this.reportedAt = reportedAt;
        this.title = title;
        this.description = description;
        this.reporterName = reporterName;
        this.reporterEmail = reporterEmail;
        this.reporterPhone = reporterPhone;
        this.reporterUserId = reporterUserId;
        this.importedAt = importedAt;
    }

    public void assign(UUID assigneeId, String priority) {
        this.assigneeId = assigneeId;
        this.priority = priority;
        this.state = assigneeId == null ? "pending" : "assigned";
    }

    public boolean addPhoto(ReportPhoto photo) {
        if (photos.stream().anyMatch(existing -> existing.getExternalId().equals(photo.getExternalId()))) {
            return false;
        }
        photos.add(photo);
        return true;
    }
}
