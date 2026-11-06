package cd.gouv.finances.signalements.ocr.entity;

import cd.gouv.finances.signalements.ocr.ReadingQuality;
import cd.gouv.finances.signalements.ocr.ReadingStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "document_reading")
@Getter
public class DocumentReading {

    @Id
    private UUID id;

    @Column(name = "document_reference", nullable = false, length = 200)
    private String documentReference;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReadingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ReadingQuality quality;

    @Column(length = 64)
    private String model;

    @Column(name = "schema_version", nullable = false, length = 32)
    private String schemaVersion;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "reading", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StoredField> fields = new ArrayList<>();

    protected DocumentReading() {
    }

    public DocumentReading(
            UUID id,
            String documentReference,
            String fingerprint,
            ReadingStatus status,
            ReadingQuality quality,
            String model,
            String schemaVersion,
            String errorCode,
            Instant createdAt) {
        this.id = id;
        this.documentReference = documentReference;
        this.fingerprint = fingerprint;
        this.status = status;
        this.quality = quality;
        this.model = model;
        this.schemaVersion = schemaVersion;
        this.errorCode = errorCode;
        this.createdAt = createdAt;
    }

    public void add(StoredField field) {
        fields.add(field);
        field.attach(this);
    }
}
