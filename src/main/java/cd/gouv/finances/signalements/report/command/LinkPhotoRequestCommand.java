package cd.gouv.finances.signalements.report.command;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.dto.PhotoRequestResponse;
import cd.gouv.finances.signalements.report.entity.PhotoRequest;
import cd.gouv.finances.signalements.report.repository.PhotoRequestRepository;
import cd.gouv.finances.signalements.report.repository.ReportRepository;
import cd.gouv.finances.signalements.report.validation.AnalysisJobs;
import cd.gouv.finances.signalements.report.validation.AssessmentDecision;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LinkPhotoRequestCommand {
    private final PhotoRequestRepository requests;
    private final ReportRepository reports;
    private final JdbcTemplate jdbc;
    private final AnalysisJobs jobs;
    private final AssessmentDecision decisions;
    private final Clock clock;

    @PreAuthorize("hasAuthority('report.review')")
    @Transactional
    public PhotoRequestResponse execute(UUID reportId, UUID requestId, String referenceCode, UUID actorId) {
        jobs.lock();
        jdbc.query("select id from app.photo_request where id=? for update",(r,n)->r.getObject(1,UUID.class),requestId);
        var request=requests.findById(requestId).filter(r->r.getReportId().equals(reportId))
                .orElseThrow(()->new ReportRejected("request-absent",404));
        var matches=reports.findIdsByVisibleReference(referenceCode.trim());
        if (matches.isEmpty()) throw new ReportRejected("report-absent",404);
        if (matches.size()!=1) throw new ReportRejected("ambiguous-report",409);
        var source=matches.getFirst();
        if (source.equals(reportId)) throw new ReportRejected("same-report",400);
        if (PhotoRequest.LINKED.equals(request.getStatus())) {
            if (!source.equals(request.getLinkedReportId())) throw new ReportRejected("already-linked",409);
            return RequestPhotoCommand.response(request,referenceCode.trim());
        }
        var valid=jdbc.queryForObject("""
                select exists(select 1 from app.report target join app.report incoming on incoming.id=?
                  where target.id=? and target.validation_state='doubt' and incoming.validation_state in ('queued','analyzing','doubt','rejected')
                    and not exists(select 1 from app.prime pr join app.payment_statement_line l on l.prime_id=pr.id
                      join app.payment_attempt pay on pay.line_id=l.id where pr.report_id in (target.id,incoming.id)
                      and pay.result='paid' and pay.reference_code not like 'SIM-%')
                    and incoming.reported_at>=target.reported_at and target.source=incoming.source and (
                      (nullif(trim(target.reporter_user_id),'') is not null and target.reporter_user_id=incoming.reporter_user_id)
                      or ((nullif(trim(target.reporter_user_id),'') is null or nullif(trim(incoming.reporter_user_id),'') is null)
                        and regexp_replace(coalesce(target.reporter_phone,''),'[^0-9]','','g')<>''
                        and regexp_replace(target.reporter_phone,'[^0-9]','','g')=regexp_replace(incoming.reporter_phone,'[^0-9]','','g')))
                    and not exists(select 1 from app.photo_request p where p.linked_report_id=incoming.id and p.id<>?)
                    and not exists(select 1 from app.duplicate_alert d where (d.report_id=incoming.id or d.other_report_id=incoming.id)
                      and d.status='confirmed' and d.priority_report_id<>incoming.id))
                """,Boolean.class,source,reportId,requestId);
        if (!Boolean.TRUE.equals(valid)) throw new ReportRejected("photo-link-owner",409);
        cd.gouv.finances.signalements.report.payment.PaymentHold.requireNoPending(jdbc,reportId);
        cd.gouv.finances.signalements.report.payment.PaymentHold.requireNoPending(jdbc,source);
        var copies=jdbc.query("""
                select id,content_type,content,fingerprint from app.report_photo where report_id=? order by external_id,id
                """,(r,n)->new Object[]{r.getObject(1,UUID.class),r.getString(2),r.getBytes(3),r.getString(4)},source);
        if(copies.isEmpty()) throw new ReportRejected("photo-absent",400);
        for(var copy:copies) {
            var id=UUID.randomUUID();
            jdbc.update("""
                    insert into app.report_photo(id,report_id,external_id,content_type,content,fingerprint,replacement_request_id)
                    values(?,?,?,?,?,?,?)
                    """,id,reportId,"NP-"+copy[0],copy[1],copy[2],copy[3],requestId);
        }
        request.link(source,clock.instant());
        requests.saveAndFlush(request);
        jobs.enqueue(reportId,true);
        // Select among the received pieces after extraction, rather than the superseded photo.
        var left=reportId.compareTo(source)<0?reportId:source;
        var right=reportId.compareTo(source)<0?source:reportId;
        jdbc.update("""
                insert into app.duplicate_alert(id,report_id,other_report_id,criterion,explanation,strength,status,
                  reason,actor_id,priority_report_id,created_at,decided_at)
                values(?,?,?,'photo','Nouvelle photo reçue pour la même facture.','likely','confirmed',
                  'Pièce rattachée au dossier d’origine.',?,?,current_timestamp,current_timestamp)
                on conflict(least(report_id,other_report_id),greatest(report_id,other_report_id),criterion) do update set status='confirmed',priority_report_id=excluded.priority_report_id,
                  reason=excluded.reason,actor_id=excluded.actor_id,decided_at=current_timestamp
                """,UUID.randomUUID(),left,right,actorId,reportId);
        var done=jdbc.queryForObject("select job_state='done' from app.report_assessment where report_id=?",Boolean.class,source);
        if(Boolean.TRUE.equals(done)) decisions.apply(source,"manual",actorId);
        return RequestPhotoCommand.response(request,referenceCode.trim());
    }
}
