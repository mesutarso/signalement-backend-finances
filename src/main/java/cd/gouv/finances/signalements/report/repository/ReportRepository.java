package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.Report;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReportRepository extends JpaRepository<Report, UUID>, JpaSpecificationExecutor<Report> {

    @EntityGraph(attributePaths = "photos")
    Optional<Report> findBySourceAndExternalId(String source, String externalId);

    @Override
    @EntityGraph(attributePaths = "photos")
    Optional<Report> findById(UUID id);

    Page<Report> findAllByOrderByReportedAtDescReferenceCodeAsc(Pageable pageable);
}
