package cd.gouv.finances.signalements.report.correction;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cd.gouv.finances.signalements.report.ReportReference;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.repository.DecisionCorrectionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class FallConfirmationCommandTest {

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private ReportReference reportReference;
    @Mock
    private DecisionCorrectionRepository decisionCorrectionRepository;
    @Test
    void refusesWhenTheLatestDecisionIsNotAConfirmation() {
        var reportId = UUID.randomUUID();
        when(reportReference.require("SIG-1")).thenReturn(new ReportReference.Match(reportId, "SIG-1"));
        when(jdbc.query(anyString(), any(RowMapper.class), eq(reportId))).thenReturn(List.of());
        var command = new FallConfirmationCommand(
                jdbc,
                reportReference,
                decisionCorrectionRepository,
                Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC), org.mockito.Mockito.mock(cd.gouv.finances.signalements.report.prime.BeneficiaryRegistry.class), org.mockito.Mockito.mock(cd.gouv.finances.signalements.report.validation.AssessmentStore.class), org.mockito.Mockito.mock(cd.gouv.finances.signalements.report.validation.FixedRewards.class));

        assertThatThrownBy(() -> command.execute("SIG-1", "La confirmation tombe.", UUID.randomUUID()))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("correction-state");
        verify(decisionCorrectionRepository, never()).save(any());
    }
}
