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
@Table(name = "point_movement")
@Getter
public class PointMovement {

    public static final String CREDIT = "credit";
    public static final String REVERSAL = "reversal";

    @Id
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "declarant_id", nullable = false, length = 80)
    private String declarantId;

    @Column(name = "tax_id", nullable = false, length = 40)
    private String taxId;

    @Column(nullable = false, length = 32)
    private String finding;

    @Column(name = "base_points", nullable = false)
    private int basePoints;

    @Column(name = "bonus_points", nullable = false)
    private int bonusPoints;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(name = "rule_version", nullable = false, length = 32)
    private String ruleVersion;

    @Column(name = "dgi_reference", length = 80)
    private String dgiReference;

    @Column(length = 500)
    private String reason;

    @Column(name = "filed_on", nullable = false)
    private LocalDate filedOn;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected PointMovement() {
    }

    public PointMovement(
            UUID id,
            UUID reportId,
            String declarantId,
            String taxId,
            String finding,
            int basePoints,
            int bonusPoints,
            String kind,
            String ruleVersion,
            String dgiReference,
            String reason,
            LocalDate filedOn,
            UUID actorId,
            Instant recordedAt) {
        this.id = id;
        this.reportId = reportId;
        this.declarantId = declarantId;
        this.taxId = taxId;
        this.finding = finding;
        this.basePoints = basePoints;
        this.bonusPoints = bonusPoints;
        this.kind = kind;
        this.ruleVersion = ruleVersion;
        this.dgiReference = dgiReference;
        this.reason = reason;
        this.filedOn = filedOn;
        this.actorId = actorId;
        this.recordedAt = recordedAt;
    }
}
