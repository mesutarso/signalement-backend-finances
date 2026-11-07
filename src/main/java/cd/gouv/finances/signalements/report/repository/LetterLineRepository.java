package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.LetterLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LetterLineRepository extends JpaRepository<LetterLine, UUID> {

    List<LetterLine> findByLetterIdOrderByPositionAsc(UUID letterId);
}
