package cd.gouv.finances.signalements.report.validation;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reclassify stored readings once per rule version, without calling the OCR provider. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name="app.validation.analysis-enabled", havingValue="true", matchIfMissing=true)
public class ScreenImportedReports {
    private final JdbcTemplate jdbc;
    private final RecheckScreening screening;
    private final AnalysisReplay replay;

    @Scheduled(fixedDelayString="${app.validation.screening-delay:10000}")
    public void run() {
        var ids = jdbc.query("""
                select r.id from app.report r join app.report_assessment a on a.report_id=r.id
                where r.validation_state='doubt' and a.job_state='done'
                  and (?::timestamptz is null or a.updated_at>=? or r.imported_at>=? or a.vat_charged='yes'
                    or not exists(select 1 from app.report_photo p where p.report_id=r.id))
                  and not exists(select 1 from app.assessment_event e where e.report_id=r.id and e.rule_version=?)
                  and not exists(select 1 from app.prime p join app.payment_statement_line l on l.prime_id=p.id
                    join app.payment_attempt pay on pay.line_id=l.id where p.report_id=r.id
                    and pay.result='paid' and pay.reference_code not like 'SIM-%')
                  and not exists(select 1 from app.prime p join app.payment_statement_line l on l.prime_id=p.id
                    join app.payment_transfer t on t.line_id=l.id where p.report_id=r.id
                    and t.environment='production' and t.state in ('submitting','pending','unknown'))
                order by (a.vat_charged='yes') desc,r.reported_at,r.id limit 25
                """, (r,n) -> r.getObject(1, UUID.class), cutoff(), cutoff(), cutoff(), ValidationRules.VERSION);
        ids.forEach(screening::execute);
    }

    private java.sql.Timestamp cutoff() { return replay.before() == null ? null : java.sql.Timestamp.from(replay.before()); }
}
