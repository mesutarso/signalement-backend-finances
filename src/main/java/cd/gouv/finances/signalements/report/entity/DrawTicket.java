package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "draw_ticket")
@Getter
public class DrawTicket {

    public static final String MONTHLY = "monthly";
    public static final String FINAL = "final";

    @Id
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "report_reference", nullable = false, length = 80)
    private String reportReference;

    @Column(name = "person_key", nullable = false, length = 80)
    private String personKey;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    protected DrawTicket() {
    }

    public DrawTicket(UUID id, UUID reportId, String reportReference, String personKey, String kind, LocalDate periodStart) {
        this.id = id;
        this.reportId = reportId;
        this.reportReference = reportReference;
        this.personKey = personKey;
        this.kind = kind;
        this.periodStart = periodStart;
    }
}
