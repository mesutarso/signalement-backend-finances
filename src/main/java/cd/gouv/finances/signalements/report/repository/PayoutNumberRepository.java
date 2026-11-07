package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.PayoutNumber;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutNumberRepository extends JpaRepository<PayoutNumber, UUID> {

    Optional<PayoutNumber> findByDeclarantId(String declarantId);
}
