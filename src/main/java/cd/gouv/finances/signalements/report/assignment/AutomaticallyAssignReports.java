package cd.gouv.finances.signalements.report.assignment;

import cd.gouv.finances.signalements.report.prime.BeneficiaryRegistry;
import cd.gouv.finances.signalements.report.validation.ValidationRules;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.validation.analysis-enabled", havingValue = "true", matchIfMissing = true)
public class AutomaticallyAssignReports {
    private final JdbcTemplate jdbc;
    private final BeneficiaryRegistry beneficiaries;

    @Scheduled(fixedDelayString = "${app.validation.analysis-delay:2000}")
    @Transactional
    public void run() {
        beneficiaries.lock();
        for (var index = 0; index < 25; index++) {
            if (assignNext() == 0) return;
        }
    }

    private int assignNext() {
        return jdbc.update("""
                with agent as (
                    select a.id from app.user_account a
                    left join app.report work on work.assignee_id = a.id and work.validation_state = 'doubt'
                    where a.role = 'AGENT' and a.enabled and a.deleted_at is null
                      and (a.locked_until is null or a.locked_until <= current_timestamp)
                    group by a.id, a.username order by count(work.id), a.username, a.id limit 1
                ), dossier as (
                    select r.id from app.report r cross join agent
                    where r.validation_state = 'doubt' and r.assignee_id is null
                      and not exists (
                        select 1 from (
                          select h.assignee_id, h.previous_assignee_id from app.report_assignment h
                          where h.report_id = r.id order by h.recorded_at desc, h.id desc limit 1
                        ) latest where latest.assignee_id is null and latest.previous_assignee_id is not null
                      )
                    order by r.reported_at, r.id limit 1 for update of r skip locked
                ), assigned as (
                    update app.report r set assignee_id = agent.id, state = 'assigned', version = r.version + 1
                    from dossier cross join agent where r.id = dossier.id
                    returning r.id, r.assignee_id, r.priority, r.state
                )
                insert into app.report_assignment
                    (id, report_id, actor_id, assignee_id, priority, previous_priority, state, recorded_at)
                select gen_random_uuid(), id, ?, assignee_id, priority, priority, state, current_timestamp from assigned
                """, ValidationRules.AUTOMATION);
    }
}
