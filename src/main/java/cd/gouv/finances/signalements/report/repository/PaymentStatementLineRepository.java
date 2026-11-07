package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.PaymentStatementLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentStatementLineRepository extends JpaRepository<PaymentStatementLine, UUID> {

    List<PaymentStatementLine> findByStatementId(UUID statementId);
}
