package cd.gouv.finances.signalements.report.repository;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Collection;
import java.time.Instant;
import cd.gouv.finances.signalements.report.entity.ReportImport;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportImportRepository extends JpaRepository<ReportImport, UUID> {
    Page<ReportImport> findAllByOrderByStartedAtDesc(Pageable pageable);

    java.util.Optional<ReportImport> findFirstByStatusOrderByStartedAtDesc(String status);

    boolean existsByStatusIn(Collection<String> statuses);
    @Transactional
    @Modifying
    @Query("update ReportImport r set r.heartbeatAt = :now where r.id = :id and r.status in ('queued', 'running')")
    int heartbeat(UUID id, Instant now);

    @Transactional
    @Modifying
    @Query("update ReportImport r set r.status = 'failed', r.errorCode = 'interrupted', r.finishedAt = :now where r.status in ('queued', 'running') and (r.heartbeatAt < :cutoff or (r.heartbeatAt is null and r.startedAt < :cutoff))")
    int failInterrupted(Instant cutoff, Instant now);
}

