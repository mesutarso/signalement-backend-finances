package cd.gouv.finances.signalements.report.command;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.dto.PhotoRequestResponse;
import cd.gouv.finances.signalements.report.entity.PhotoRequest;
import cd.gouv.finances.signalements.report.message.DeclarantMessageText;
import cd.gouv.finances.signalements.report.validation.AnalysisJobs;
import cd.gouv.finances.signalements.report.validation.AssessmentStore;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RequestPhotoCommand {
    private final JdbcTemplate jdbc;
    private final AnalysisJobs jobs;
    private final AssessmentStore assessments;
    private final Clock clock;

    @PreAuthorize("hasAuthority('report.review')")
    @Transactional
    public PhotoRequestResponse execute(UUID reportId, UUID photoId, String reason, UUID actorId) {
        jobs.lock();
        var assessment = assessments.lock(reportId);
        if (!"doubt".equals(assessment.state()) || !"done".equals(assessment.jobState()))
            throw new ReportRejected("photo-request-state", 409);
        var kept = reason == null ? "" : reason.trim();
        if (kept.isBlank() || kept.length() > 500) throw new ReportRejected("photo-request-reason", 400);
        if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from app.report_photo where id=? and report_id=?)", Boolean.class, photoId, reportId)))
            throw new ReportRejected("photo-absent", 404);
        var open = jdbc.query("select id from app.photo_request where photo_id=? and status='open'", (r,n)->r.getObject(1,UUID.class),photoId);
        if (!open.isEmpty()) return read(open.getFirst());
        var number = jdbc.queryForObject("select nextval('app.photo_request_seq')", Long.class);
        var reference = "NP-%08d".formatted(number);
        var id = UUID.randomUUID();
        var now = Timestamp.from(clock.instant());
        jdbc.update("""
                insert into app.photo_request(id,reference_code,report_id,photo_id,reason,basis,status,actor_id,created_at,notice)
                values(?,?,?,?,?,'review','open',?,?,'waiting')
                """,id,reference,reportId,photoId,kept,actorId,now);
        jdbc.update("""
                insert into app.declarant_message(id,report_id,report_reference,kind,body,status,prepared_by,prepared_at,photo_request_id)
                select ?,id,reference_code,'photo',?,'prepared',?,?,? from app.report where id=?
                """,UUID.randomUUID(),DeclarantMessageText.body("photo",reference),actorId,now,id,reportId);
        return read(id);
    }

    private PhotoRequestResponse read(UUID id) {
        return jdbc.query("""
                select p.*,m.id as message_id,m.status as message_status,m.body as message_body
                from app.photo_request p left join app.declarant_message m on m.photo_request_id=p.id where p.id=?
                """,(r,n)->new PhotoRequestResponse(id,r.getObject("photo_id",UUID.class),r.getString("reference_code"),
                r.getString("reason"),r.getString("basis"),r.getString("status"),null,r.getTimestamp("created_at").toInstant(),
                null,null,r.getString("notice"),r.getObject("message_id",UUID.class),r.getString("message_status"),r.getString("message_body")),id).getFirst();
    }

    static PhotoRequestResponse response(PhotoRequest request, String linkedIdentifier) {
        return new PhotoRequestResponse(request.getId(),request.getPhotoId(),request.getReferenceCode(),request.getReason(),
                request.getBasis(),request.getStatus(),null,request.getCreatedAt(),request.getResolvedAt(),linkedIdentifier,request.getNotice());
    }
}
