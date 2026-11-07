package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.Report;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ReportRepository extends JpaRepository<Report, UUID>, JpaSpecificationExecutor<Report> {

    @EntityGraph(attributePaths = "photos")
    Optional<Report> findBySourceAndExternalId(String source, String externalId);

    @Override
    @EntityGraph(attributePaths = "photos")
    Optional<Report> findById(UUID id);

    @Query("select report.id from Report report where report.externalId = :reference or report.referenceCode = :reference")
    List<UUID> findIdsByVisibleReference(String reference);

    @Query("select report.id as id, report.externalId as externalId from Report report where report.id in :ids")
    List<ReportReference> findReferencesByIdIn(Collection<UUID> ids);

    Page<Report> findAllByOrderByReportedAtDescReferenceCodeAsc(Pageable pageable);
}
