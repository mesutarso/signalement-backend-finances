package cd.gouv.finances.signalements.report.validation;

import cd.gouv.finances.signalements.report.duplicate.AutomaticDuplicates;
import cd.gouv.finances.signalements.report.duplicate.PrepareDuplicateAlerts;
import cd.gouv.finances.signalements.report.prime.BeneficiaryRegistry;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RecheckScreening {
    private final JdbcTemplate jdbc;
    private final BeneficiaryRegistry beneficiaries;
    private final PrepareDuplicateAlerts candidates;
    private final AutomaticDuplicates duplicates;
    private final AssessmentDecision decisions;
    private final AnalysisReplay replay;

    @Transactional
    public void execute(UUID id) {
        beneficiaries.lock();
        var eligible = jdbc.queryForObject("""
                select exists(select 1 from app.report r join app.report_assessment a on a.report_id=r.id
                  where r.id=? and r.validation_state='doubt' and a.job_state='done'
                    and (?::timestamptz is null or a.updated_at>=? or r.imported_at>=? or a.vat_charged='yes'
                      or not exists(select 1 from app.report_photo p where p.report_id=r.id))
                    and not exists(select 1 from app.assessment_event e where e.report_id=r.id and e.rule_version=?)
                    and not exists(select 1 from app.prime p join app.payment_statement_line l on l.prime_id=p.id
                      join app.payment_attempt pay on pay.line_id=l.id where p.report_id=r.id
                      and pay.result='paid' and pay.reference_code not like 'SIM-%')
                    and not exists(select 1 from app.prime p join app.payment_statement_line l on l.prime_id=p.id
                      join app.payment_transfer t on t.line_id=l.id where p.report_id=r.id
                      and t.environment='production' and t.state in ('submitting','pending','unknown')))
                """, Boolean.class, id, cutoff(), cutoff(), cutoff(), ValidationRules.VERSION);
        if (!Boolean.TRUE.equals(eligible)) return;
        candidates.execute(id);
        duplicates.execute(id);
        duplicates.prepareUniqueness(id);
        decisions.apply(id, "automatic", null);
    }

    private java.sql.Timestamp cutoff() { return replay.before() == null ? null : java.sql.Timestamp.from(replay.before()); }
}
