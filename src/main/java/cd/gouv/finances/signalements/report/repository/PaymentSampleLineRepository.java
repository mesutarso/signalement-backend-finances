package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.PaymentSampleLine;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentSampleLineRepository extends JpaRepository<PaymentSampleLine, UUID> {
}
