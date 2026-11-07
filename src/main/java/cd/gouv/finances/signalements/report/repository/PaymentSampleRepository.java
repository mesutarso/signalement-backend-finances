package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.PaymentSample;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentSampleRepository extends JpaRepository<PaymentSample, UUID> {

    Optional<PaymentSample> findByPeriodStartAndPeriodEnd(LocalDate periodStart, LocalDate periodEnd);

    @Query(value = "select nextval('app.payment_sample_seq')", nativeQuery = true)
    long nextNumber();
}
