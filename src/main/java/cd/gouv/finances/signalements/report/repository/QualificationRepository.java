package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.Qualification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QualificationRepository extends JpaRepository<Qualification, UUID> {

    List<Qualification> findByReportIdOrderByRecordedAtDesc(UUID reportId);

    @Query(value = "select coalesce(sum(base_points + bonus_points), 0) > 0 from app.point_movement where report_id = :reportId", nativeQuery = true)
    boolean hasCreditedPoints(UUID reportId);
}
