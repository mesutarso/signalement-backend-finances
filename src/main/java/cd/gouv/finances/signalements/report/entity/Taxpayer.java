package cd.gouv.finances.signalements.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "taxpayer")
@Getter
public class Taxpayer {

    @Id
    private UUID id;

    @Column(name = "tax_id", nullable = false, length = 40)
    private String taxId;

    @Column(length = 160)
    private String name;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected Taxpayer() {
    }

    public Taxpayer(UUID id, String taxId, String name, Instant recordedAt) {
        this.id = id;
        this.taxId = taxId;
        this.name = name;
        this.verified = false;
        this.recordedAt = recordedAt;
    }

    public void verify(Instant at) {
        this.verified = true;
        this.verifiedAt = at;
    }
}
