package cd.gouv.finances.signalements.report.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.assignment.AssigneeDirectory;
import cd.gouv.finances.signalements.report.entity.Report;
import cd.gouv.finances.signalements.report.repository.ReportAssignmentRepository;
import cd.gouv.finances.signalements.report.repository.ReportRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssignReportCommandTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ReportAssignmentRepository reportAssignmentRepository;

    @Mock
    private AssigneeDirectory assigneeDirectory;

    @Test
    void rejectsAStaleVersion() {
        var id = UUID.randomUUID();
        var report = report(id);
        when(reportRepository.findById(id)).thenReturn(Optional.of(report));
        var command = command();

        assertThatThrownBy(() -> command.execute(id, 4, UUID.randomUUID(), "high", UUID.randomUUID()))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("stale");
        verify(reportAssignmentRepository, never()).save(any());
    }

    @Test
    void recordsAnAssignment() {
        var id = UUID.randomUUID();
        var agent = UUID.randomUUID();
        var report = report(id);
        when(reportRepository.findById(id)).thenReturn(Optional.of(report));
        when(assigneeDirectory.isAssignableAgent(agent)).thenReturn(true);
        when(reportRepository.saveAndFlush(report)).thenReturn(report);
        var command = command();

        command.execute(id, 0, agent, "urgent", UUID.randomUUID());

        assertThat(report.getAssigneeId()).isEqualTo(agent);
        assertThat(report.getState()).isEqualTo("assigned");
        assertThat(report.getPriority()).isEqualTo("urgent");
        verify(reportAssignmentRepository).save(any());
    }

    private AssignReportCommand command() {
        return new AssignReportCommand(
                reportRepository,
                reportAssignmentRepository,
                assigneeDirectory,
                Clock.fixed(Instant.parse("2026-11-06T10:00:00Z"), ZoneOffset.UTC));
    }

    private static Report report(UUID id) {
        return new Report(
                id,
                "signalement-001",
                "facno",
                "signalement-001",
                LocalDate.parse("2026-10-19"),
                LocalDateTime.parse("2026-10-19T10:30:00"),
                "Facture non délivrée",
                "Description",
                null,
                null,
                null,
                null,
                Instant.parse("2026-11-06T00:00:00Z"));
    }
}
