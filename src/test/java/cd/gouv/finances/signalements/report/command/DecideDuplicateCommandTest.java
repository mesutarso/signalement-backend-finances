package cd.gouv.finances.signalements.report.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.duplicate.DuplicatePriority;
import cd.gouv.finances.signalements.report.duplicate.PrepareDuplicateAlerts;
import cd.gouv.finances.signalements.report.entity.DuplicateAlert;
import cd.gouv.finances.signalements.report.repository.DuplicateAlertRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DecideDuplicateCommandTest {

    @Mock
    private DuplicateAlertRepository duplicateAlertRepository;

    @Mock
    private PrepareDuplicateAlerts prepareDuplicateAlerts;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T20:00:00Z"), ZoneOffset.UTC);

    @Test
    void confirmsWithoutReplacingTheReport() {
        var current = UUID.randomUUID();
        var earlier = UUID.randomUUID();
        var alert = new DuplicateAlert(
                UUID.randomUUID(), earlier, current, "photo", "Même image", "likely", clock.instant());
        when(duplicateAlertRepository.findById(alert.getId())).thenReturn(Optional.of(alert));
        when(prepareDuplicateAlerts.winner(earlier, current)).thenReturn(new DuplicatePriority.Side(
                earlier, "premier", LocalDateTime.parse("2026-10-01T08:00:00"), "a", true));
        var command = new DecideDuplicateCommand(duplicateAlertRepository, prepareDuplicateAlerts, clock, org.mockito.Mockito.mock(cd.gouv.finances.signalements.report.prime.BeneficiaryRegistry.class), org.mockito.Mockito.mock(cd.gouv.finances.signalements.report.validation.AnalysisJobs.class));

        command.execute(current, alert.getId(), "confirm", "  Même facture.  ", UUID.randomUUID());

        assertThat(alert.getStatus()).isEqualTo(DuplicateAlert.CONFIRMED);
        assertThat(alert.getPriorityReportId()).isEqualTo(earlier);
        assertThat(alert.getReason()).isEqualTo("Même facture.");
    }

    @Test
    void aSecondOppositeDecisionIsRejected() {
        var current = UUID.randomUUID();
        var other = UUID.randomUUID();
        var alert = new DuplicateAlert(
                UUID.randomUUID(), current, other, "number", "Même numéro", "possible", clock.instant());
        alert.dismiss("Pas la même opération", UUID.randomUUID(), clock.instant());
        when(duplicateAlertRepository.findById(alert.getId())).thenReturn(Optional.of(alert));
        var command = new DecideDuplicateCommand(duplicateAlertRepository, prepareDuplicateAlerts, clock, org.mockito.Mockito.mock(cd.gouv.finances.signalements.report.prime.BeneficiaryRegistry.class), org.mockito.Mockito.mock(cd.gouv.finances.signalements.report.validation.AnalysisJobs.class));

        assertThatThrownBy(() -> command.execute(current, alert.getId(), "confirm", "Finalement oui", UUID.randomUUID()))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("duplicate-decided");
        assertThat(alert.getStatus()).isEqualTo(DuplicateAlert.DISMISSED);
    }
}
