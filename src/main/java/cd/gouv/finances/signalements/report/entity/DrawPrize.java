package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "draw_prize")
@Getter
public class DrawPrize {

    @Id
    private UUID id;

    @Column(name = "draw_id", nullable = false)
    private UUID drawId;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "report_reference", nullable = false, length = 80)
    private String reportReference;

    @Column(nullable = false, length = 80)
    private String label;

    protected DrawPrize() {
    }

    public DrawPrize(UUID id, UUID drawId, UUID ticketId, String reportReference, String label) {
        this.id = id;
        this.drawId = drawId;
        this.ticketId = ticketId;
        this.reportReference = reportReference;
        this.label = label;
    }
}
