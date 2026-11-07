package cd.gouv.finances.signalements.report.prime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cd.gouv.finances.signalements.report.entity.Prime;
import cd.gouv.finances.signalements.report.points.PointsAward;
import cd.gouv.finances.signalements.report.prime.PrimeContext.Phone;
import cd.gouv.finances.signalements.report.repository.PrimeRepository;
import cd.gouv.finances.signalements.report.rule.ActiveRule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssessPrimeCommandTest {

    @Mock
    private PrimeContext primeContext;

    @Mock
    private PrimeRepository primeRepository;

    @Mock
    private RecordPayoutCommand recordPayoutCommand;

    @Mock
    private PrimeGuard primeGuard;

    @Mock
    private ActiveRule activeRule;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-07T01:00:00Z"), ZoneOffset.UTC);

    @Test
    void proposesEachNewlyReachedTierOnceForTheDeclarantPhone() {
        when(primeContext.netPoints("user-1", PointsAward.RULE)).thenReturn(100);
        when(primeContext.phone("user-1", PointsAward.RULE)).thenReturn(new Phone("0990000001", PrimeContext.READY));
        when(primeRepository.findByDeclarantIdAndRuleVersion("user-1", PointsAward.RULE)).thenReturn(List.of());
        when(recordPayoutCommand.execute(any(), any())).thenReturn(Optional.empty());
        when(activeRule.requireActive()).thenReturn(ActiveRule.cdc());
        var command = new AssessPrimeCommand(primeContext, primeRepository, recordPayoutCommand, primeGuard, activeRule, clock);

        var changes = command.execute("user-1", UUID.randomUUID());

        var saved = ArgumentCaptor.forClass(Prime.class);
        verify(primeRepository, org.mockito.Mockito.times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Prime::getThreshold).containsExactly(30, 60, 100);
        assertThat(saved.getAllValues()).extracting(Prime::getAmount).containsExactly(10_000, 20_000, 30_000);
        assertThat(saved.getAllValues()).allMatch(prime -> "0990000001".equals(prime.getPhone()));
        assertThat(changes).hasSize(3);
    }

    @Test
    void doesNotProposeWhenTheDeclarantHasSeveralPhonesAndWithdrawsAnOpenTier() {
        var open = new Prime(UUID.randomUUID(), "user-1", "0990000001", PointsAward.RULE, 30, 10_000, UUID.randomUUID(), clock.instant());
        when(primeContext.netPoints("user-1", PointsAward.RULE)).thenReturn(40);
        when(primeContext.phone("user-1", PointsAward.RULE)).thenReturn(new Phone(null, PrimeContext.SEVERAL));
        when(primeRepository.findByDeclarantIdAndRuleVersion("user-1", PointsAward.RULE)).thenReturn(new ArrayList<>(List.of(open)));
        when(recordPayoutCommand.execute(any(), any())).thenReturn(Optional.empty());
        when(activeRule.requireActive()).thenReturn(ActiveRule.cdc());
        var command = new AssessPrimeCommand(primeContext, primeRepository, recordPayoutCommand, primeGuard, activeRule, clock);

        var changes = command.execute("user-1", UUID.randomUUID());

        verify(primeRepository, never()).save(any());
        assertThat(open.getStatus()).isEqualTo(Prime.WITHDRAWN);
        assertThat(changes).extracting(AssessPrimeCommand.Change::action).containsExactly("prime-withdrawal");
    }

    @Test
    void leavesAnApprovedTierUntouchedWhenThePhoneStaysTheSame() {
        var approved = new Prime(UUID.randomUUID(), "user-1", "0990000001", PointsAward.RULE, 30, 10_000, UUID.randomUUID(), clock.instant());
        approved.approve(UUID.randomUUID(), clock.instant());
        when(primeContext.netPoints("user-1", PointsAward.RULE)).thenReturn(40);
        when(primeContext.phone("user-1", PointsAward.RULE)).thenReturn(new Phone("0990000001", PrimeContext.READY));
        when(primeRepository.findByDeclarantIdAndRuleVersion("user-1", PointsAward.RULE)).thenReturn(List.of(approved));
        when(recordPayoutCommand.execute(any(), any())).thenReturn(Optional.empty());
        when(activeRule.requireActive()).thenReturn(ActiveRule.cdc());
        var command = new AssessPrimeCommand(primeContext, primeRepository, recordPayoutCommand, primeGuard, activeRule, clock);

        var changes = command.execute("user-1", UUID.randomUUID());

        verify(primeRepository, never()).save(any());
        assertThat(approved.getStatus()).isEqualTo(Prime.APPROVED);
        assertThat(changes).isEmpty();
    }
}
