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
@Table(name = "campaign_draw")
@Getter
public class CampaignDraw {

    public static final String DRAWN = "drawn";
    public static final String PUBLISHED = "published";

    @Id
    private UUID id;

    @Column(name = "reference_code", nullable = false, length = 32)
    private String referenceCode;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false, length = 160)
    private String witness;

    @Column(nullable = false)
    private long seed;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "drawn_by", nullable = false)
    private UUID drawnBy;

    @Column(name = "drawn_at", nullable = false)
    private Instant drawnAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected CampaignDraw() {
    }

    public CampaignDraw(UUID id, String referenceCode, String kind, LocalDate periodStart, String witness, long seed, UUID drawnBy, Instant drawnAt) {
        this.id = id;
        this.referenceCode = referenceCode;
        this.kind = kind;
        this.periodStart = periodStart;
        this.witness = witness;
        this.seed = seed;
        this.status = DRAWN;
        this.drawnBy = drawnBy;
        this.drawnAt = drawnAt;
    }

    public void publish(UUID actorId, Instant at) {
        this.status = PUBLISHED;
        this.publishedBy = actorId;
        this.publishedAt = at;
    }
}
