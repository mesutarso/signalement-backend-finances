package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.Reexamination;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReexaminationRepository extends JpaRepository<Reexamination, UUID> {

    @Query(value = "select nextval('app.reexamination_seq')", nativeQuery = true)
    long nextNumber();
}
