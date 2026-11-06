package cd.gouv.finances.signalements.ocr.entity;

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
@Table(name = "extracted_field")
@Getter
public class StoredField {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reading_id", nullable = false)
    private DocumentReading reading;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(columnDefinition = "text")
    private String value;

    @Column(nullable = false)
    private boolean present;

    protected StoredField() {
    }

    public StoredField(UUID id, String name, String value, boolean present) {
        this.id = id;
        this.name = name;
        this.value = value;
        this.present = present;
    }

    void attach(DocumentReading reading) {
        this.reading = reading;
    }
}
