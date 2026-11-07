package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.Prime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrimeRepository extends JpaRepository<Prime, UUID> {

    List<Prime> findByDeclarantId(String declarantId);

    List<Prime> findByDeclarantIdAndRuleVersion(String declarantId, String ruleVersion);
}
