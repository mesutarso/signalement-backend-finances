package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.PaymentStatementExclusion;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentStatementExclusionRepository extends JpaRepository<PaymentStatementExclusion, UUID> {
}
