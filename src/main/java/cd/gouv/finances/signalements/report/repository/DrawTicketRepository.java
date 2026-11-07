package cd.gouv.finances.signalements.report.repository;

import cd.gouv.finances.signalements.report.entity.DrawTicket;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DrawTicketRepository extends JpaRepository<DrawTicket, UUID> {

    boolean existsByReportIdAndKind(UUID reportId, String kind);

    @Query(value = "select nextval('app.campaign_draw_seq')", nativeQuery = true)
    long nextNumber();
}
