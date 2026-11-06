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
@Table(name = "report_import")
@Getter
public class ReportImport {

    @Id
    private UUID id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_reports", nullable = false)
    private int createdReports;

    @Column(name = "skipped_reports", nullable = false)
    private int skippedReports;

    @Column(name = "created_photos", nullable = false)
    private int createdPhotos;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "period_start")
    private LocalDate periodStart;
    @Column(name = "period_end")
    private LocalDate periodEnd;
    @Column(name = "trigger_type", nullable = false, length = 16)
    private String triggerType = "manual";

    public void configure(LocalDate start, LocalDate end, String trigger) {
        periodStart = start;
        periodEnd = end;
        triggerType = trigger;
    }

    public void progress(String nextStatus, int created, int skipped, int photos, String error, Instant finished) {
        status = nextStatus;
        createdReports = created;
        skippedReports = skipped;
        createdPhotos = photos;
        errorCode = error;
        finishedAt = finished;
    }

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    public void heartbeat(Instant now) { heartbeatAt = now; }

    protected ReportImport() {
    }

    public ReportImport(
            UUID id,
            Instant startedAt,
            Instant finishedAt,
            String status,
            int createdReports,
            int skippedReports,
            int createdPhotos,
            String errorCode) {
        this.id = id;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.status = status;
        this.createdReports = createdReports;
        this.skippedReports = skippedReports;
        this.createdPhotos = createdPhotos;
        this.errorCode = errorCode;
    }
}
