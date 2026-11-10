package cd.gouv.finances.signalements.report.points;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cd.gouv.finances.signalements.report.entity.PointMovement;
import cd.gouv.finances.signalements.report.points.PointsAward.Claim;
import cd.gouv.finances.signalements.report.prime.AssessPrimeCommand;
import cd.gouv.finances.signalements.report.repository.PointMovementRepository;
import cd.gouv.finances.signalements.report.rule.ActiveRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreditPointsCommandTest {

    @Mock
    private PointsContext pointsContext;

    @Mock
    private PointMovementRepository pointMovementRepository;

    @Mock
    private AssessPrimeCommand assessPrimeCommand;

    @Mock
    private ActiveRule activeRule;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T22:00:00Z"), ZoneOffset.UTC);

    @Test
    void creditsTheScaleAndKeepsTheConfirmationReference() {
        var reportId = UUID.randomUUID();
        var actorId = UUID.randomUUID();
        when(pointsContext.claim(reportId)).thenReturn(new Claim(
                "confirm", "non-normalized", "A-1", "user-1", false, 0, LocalDate.parse("2026-11-04"), List.of(), false, 0));
        when(activeRule.requireForReport(reportId)).thenReturn(ActiveRule.cdc());
        when(assessPrimeCommand.execute("user-1", actorId, PointsAward.RULE)).thenReturn(List.of());
        var command = new CreditPointsCommand(pointsContext, pointMovementRepository, assessPrimeCommand, activeRule, clock);

        command.execute(reportId, "  DGI-1  ", actorId);

        var saved = ArgumentCaptor.forClass(PointMovement.class);
        verify(pointMovementRepository).save(saved.capture());
        assertThat(saved.getValue().getBasePoints()).isEqualTo(2);
        assertThat(saved.getValue().getBonusPoints()).isEqualTo(1);
        assertThat(saved.getValue().getDgiReference()).isEqualTo("DGI-1");
        assertThat(saved.getValue().getKind()).isEqualTo("credit");
    }
}
