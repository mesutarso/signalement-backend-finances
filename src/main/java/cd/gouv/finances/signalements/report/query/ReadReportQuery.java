package cd.gouv.finances.signalements.report.query;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.assignment.AssigneeDirectory;
import cd.gouv.finances.signalements.report.dto.ReportResponse;
import cd.gouv.finances.signalements.report.entity.ReportAssignment;
import cd.gouv.finances.signalements.report.mapper.ReportMapper;
import cd.gouv.finances.signalements.report.repository.ReportAssignmentRepository;
import cd.gouv.finances.signalements.report.repository.ReportRepository;
import java.util.HashSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReadReportQuery {

    private final ReportRepository reportRepository;
    private final ReportAssignmentRepository reportAssignmentRepository;
    private final AssigneeDirectory assigneeDirectory;

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public ReportResponse execute(UUID id, boolean includeReporter) {
        var report = reportRepository.findById(id).orElseThrow(() -> new ReportRejected("report-absent", 404));
        var history = reportAssignmentRepository.findByReportIdOrderByRecordedAtDesc(id);
        var ids = new HashSet<UUID>();
        if (report.getAssigneeId() != null) {
            ids.add(report.getAssigneeId());
        }
        for (var entry : history) {
            ids.add(entry.getActorId());
            if (entry.getAssigneeId() != null) {
                ids.add(entry.getAssigneeId());
            }
            if (entry.getPreviousAssigneeId() != null) {
                ids.add(entry.getPreviousAssigneeId());
            }
        }
        return ReportMapper.toResponse(report, includeReporter, assigneeDirectory.names(ids), history);
    }
}
