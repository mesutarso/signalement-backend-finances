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
@Table(name = "letter_response")
@Getter
public class LetterResponse {

    @Id
    private UUID id;

    @Column(name = "letter_id", nullable = false)
    private UUID letterId;

    @Column(name = "received_on", nullable = false)
    private LocalDate receivedOn;

    @Column(nullable = false, length = 160)
    private String service;

    @Column(name = "reference_code", nullable = false, length = 80)
    private String referenceCode;

    @Column(nullable = false, length = 500)
    private String conclusion;

    @Column(nullable = false)
    private byte[] content;

    @Column(name = "content_type", nullable = false, length = 80)
    private String contentType;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected LetterResponse() {
    }

    public LetterResponse(
            UUID id,
            UUID letterId,
            LocalDate receivedOn,
            String service,
            String referenceCode,
            String conclusion,
            byte[] content,
            String contentType,
            String fingerprint,
            UUID actorId,
            Instant recordedAt) {
        this.id = id;
        this.letterId = letterId;
        this.receivedOn = receivedOn;
        this.service = service;
        this.referenceCode = referenceCode;
        this.conclusion = conclusion;
        this.content = content;
        this.contentType = contentType;
        this.fingerprint = fingerprint;
        this.actorId = actorId;
        this.recordedAt = recordedAt;
    }
}
