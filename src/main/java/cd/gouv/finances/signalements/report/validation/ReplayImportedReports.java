package cd.gouv.finances.signalements.report.validation;

import cd.gouv.finances.signalements.report.ReportRejected;
import java.sql.Timestamp;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.validation.analysis-enabled", havingValue = "true", matchIfMissing = true)
public class ReplayImportedReports {
    private final JdbcTemplate jdbc;
    private final AnalysisJobs jobs;
    private final AnalysisReplay replay;

    @Scheduled(fixedDelayString = "${app.validation.replay-delay:10000}")
    public void run() {
        if (replay.before() == null) return;
        var cutoff = Timestamp.from(replay.before());
        var ids = jdbc.query("""
                select a.report_id from app.report_assessment a join app.report r on r.id = a.report_id
                where a.job_state = 'done' and a.updated_at < ? and r.imported_at < ?
                  and r.validation_state <> 'legacy'
                  and exists (select 1 from app.report_photo p where p.report_id = r.id)
                  and not exists (select 1 from app.prime pr join app.payment_statement_line l on l.prime_id = pr.id
                    join app.payment_attempt pay on pay.line_id = l.id
                    where pr.report_id = r.id and pay.result = 'paid' and pay.reference_code not like 'SIM-%')
                  and not exists (select 1 from app.prime pr join app.payment_statement_line l on l.prime_id = pr.id
                    join app.payment_transfer t on t.line_id = l.id
                    where pr.report_id = r.id and t.environment = 'production' and t.state in ('submitting','pending','unknown'))
                order by r.imported_at, r.id limit 25
                """, (row, n) -> row.getObject(1, UUID.class), cutoff, cutoff);
        for (var id : ids) {
            try {
                jobs.replay(id, replay.before());
            } catch (ReportRejected conflict) {
                if (!"payment-hold".equals(conflict.code())) throw conflict;
            }
        }
    }
}
