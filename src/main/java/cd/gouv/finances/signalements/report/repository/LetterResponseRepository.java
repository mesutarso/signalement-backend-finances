package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.LetterResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LetterResponseRepository extends JpaRepository<LetterResponse, UUID> {

    Optional<LetterResponse> findByLetterIdAndReferenceCode(UUID letterId, String referenceCode);

    Optional<LetterResponse> findByIdAndLetterId(UUID id, UUID letterId);
}
