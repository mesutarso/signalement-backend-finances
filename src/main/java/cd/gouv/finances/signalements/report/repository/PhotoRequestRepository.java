package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.PhotoRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PhotoRequestRepository extends JpaRepository<PhotoRequest, UUID> {

    Optional<PhotoRequest> findFirstByPhotoIdAndStatus(UUID photoId, String status);

    List<PhotoRequest> findByReportIdOrderByCreatedAtDesc(UUID reportId);

    @Query(value = "select nextval('app.photo_request_seq')", nativeQuery = true)
    long nextNumber();
}
