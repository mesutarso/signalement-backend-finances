package cd.gouv.finances.signalements.ocr.repository;

import cd.gouv.finances.signalements.ocr.ReadingStatus;
import cd.gouv.finances.signalements.ocr.entity.DocumentReading;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReadingRepository extends JpaRepository<DocumentReading, UUID> {

    @EntityGraph(attributePaths = "fields")
    Optional<DocumentReading> findFirstByDocumentReferenceAndFingerprintAndStatusOrderByCreatedAtDesc(
            String documentReference, String fingerprint, ReadingStatus status);
}
