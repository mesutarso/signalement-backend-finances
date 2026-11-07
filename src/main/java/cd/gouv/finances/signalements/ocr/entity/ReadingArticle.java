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
@Table(name = "reading_article")
@Getter
public class ReadingArticle {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reading_id", nullable = false)
    private DocumentReading reading;

    @Column(nullable = false)
    private int position;

    @Column(length = 400)
    private String designation;

    @Column(length = 64)
    private String amount;

    protected ReadingArticle() {
    }

    public ReadingArticle(UUID id, int position, String designation, String amount) {
        this.id = id;
        this.position = position;
        this.designation = designation;
        this.amount = amount;
    }

    void attach(DocumentReading reading) {
        this.reading = reading;
    }
}
