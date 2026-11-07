package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.Taxpayer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaxpayerRepository extends JpaRepository<Taxpayer, UUID> {

    @Query("select taxpayer from Taxpayer taxpayer where lower(trim(taxpayer.taxId)) = lower(trim(:taxId))")
    Optional<Taxpayer> findByTaxId(@Param("taxId") String taxId);
}
