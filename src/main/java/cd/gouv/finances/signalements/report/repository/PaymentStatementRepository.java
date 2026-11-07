package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.PaymentStatement;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentStatementRepository extends JpaRepository<PaymentStatement, UUID> {

    Optional<PaymentStatement> findFirstByStatusIn(Collection<String> statuses);

    @Query(value = "select nextval('app.payment_statement_seq')", nativeQuery = true)
    long nextNumber();
}
