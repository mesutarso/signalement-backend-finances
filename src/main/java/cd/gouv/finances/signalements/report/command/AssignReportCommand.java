package cd.gouv.finances.signalements.report.command;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.assignment.AssigneeDirectory;
import cd.gouv.finances.signalements.report.entity.ReportAssignment;
import cd.gouv.finances.signalements.report.repository.ReportAssignmentRepository;
import cd.gouv.finances.signalements.report.repository.ReportRepository;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AssignReportCommand {

    private static final Set<String> PRIORITIES = Set.of("low", "normal", "high", "urgent");

    private final ReportRepository reportRepository;
    private final ReportAssignmentRepository reportAssignmentRepository;
    private final AssigneeDirectory assigneeDirectory;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public void execute(UUID reportId, long version, UUID assigneeId, String priority, UUID actorId) {
        var report = reportRepository.findById(reportId).orElseThrow(() -> new ReportRejected("report-absent", 404));
        if (report.getVersion() != version) {
            throw new ReportRejected("stale", 409);
        }
        var chosen = priority == null || priority.isBlank() ? null : priority;
        if (chosen != null && !PRIORITIES.contains(chosen)) {
            throw new ReportRejected("priority", 400);
        }
        if (assigneeId != null && !assigneeDirectory.isAssignableAgent(assigneeId)) {
            throw new ReportRejected("assignee", 400);
        }
        if (same(report.getAssigneeId(), assigneeId) && same(report.getPriority(), chosen)) {
            return;
        }
        var previousAssignee = report.getAssigneeId();
        var previousPriority = report.getPriority();
        report.assign(assigneeId, chosen);
        reportAssignmentRepository.save(new ReportAssignment(
                UUID.randomUUID(),
                report.getId(),
                actorId,
                assigneeId,
                previousAssignee,
                chosen,
                previousPriority,
                report.getState(),
                clock.instant()));
        try {
            reportRepository.saveAndFlush(report);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ReportRejected("stale", 409);
        }
    }

    private static boolean same(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }
}
