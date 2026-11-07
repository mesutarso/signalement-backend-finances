package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.PaymentAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    List<PaymentAttempt> findByLineIdOrderByRecordedAtAsc(UUID lineId);

    @Query(value = "select nextval('app.payment_simulation_seq')", nativeQuery = true)
    long nextNumber();
}
