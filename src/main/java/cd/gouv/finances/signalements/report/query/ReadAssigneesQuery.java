package cd.gouv.finances.signalements.report.query;

import cd.gouv.finances.signalements.report.assignment.AssigneeDirectory;
import cd.gouv.finances.signalements.report.dto.AssigneeResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReadAssigneesQuery {

    private final AssigneeDirectory assigneeDirectory;

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public List<AssigneeResponse> execute() {
        return assigneeDirectory.agents();
    }
}
