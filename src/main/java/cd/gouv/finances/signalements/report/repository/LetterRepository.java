package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.Letter;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LetterRepository extends JpaRepository<Letter, UUID> {

    Optional<Letter> findFirstByPeriodStartAndStatus(LocalDate periodStart, String status);

    @Query(value = "select nextval('app.letter_seq')", nativeQuery = true)
    long nextNumber();
}
