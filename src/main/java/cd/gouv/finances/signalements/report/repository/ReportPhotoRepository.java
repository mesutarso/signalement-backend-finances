package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.ReportPhoto;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReportPhotoRepository extends JpaRepository<ReportPhoto, UUID> {

    @Query("select photo from ReportPhoto photo where photo.id = :photoId and photo.report.id = :reportId")
    Optional<ReportPhoto> findByReportAndId(UUID reportId, UUID photoId);

    boolean existsByReport_SourceAndReport_ExternalIdAndExternalId(
            String source, String reportExternalId, String externalId);
}
