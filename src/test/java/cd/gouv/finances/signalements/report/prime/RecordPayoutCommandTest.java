package cd.gouv.finances.signalements.report.prime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cd.gouv.finances.signalements.report.entity.PayoutNumber;
import cd.gouv.finances.signalements.report.points.PointsAward;
import cd.gouv.finances.signalements.report.prime.PrimeContext.Phone;
import cd.gouv.finances.signalements.report.repository.PayoutNumberRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordPayoutCommandTest {

    @Mock
    private PrimeContext primeContext;

    @Mock
    private PayoutNumberRepository payoutNumberRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-07T01:00:00Z"), ZoneOffset.UTC);

    @Test
    void recordsTheSingleDeclarantPhone() {
        when(primeContext.phone("user-1", PointsAward.RULE)).thenReturn(new Phone("0990000001", PrimeContext.READY));
        when(payoutNumberRepository.findByDeclarantId("user-1")).thenReturn(Optional.empty());
        var command = new RecordPayoutCommand(primeContext, payoutNumberRepository, clock);

        var recorded = command.execute("user-1", UUID.randomUUID());

        var saved = ArgumentCaptor.forClass(PayoutNumber.class);
        verify(payoutNumberRepository).save(saved.capture());
        assertThat(recorded).contains(saved.getValue().getId());
        assertThat(saved.getValue().getPhone()).isEqualTo("0990000001");
        assertThat(saved.getValue().getState()).isEqualTo(PrimeContext.READY);
    }

    @Test
    void recordsSeveralPhonesWithoutKeepingANumber() {
        var current = new PayoutNumber(UUID.randomUUID(), "user-1", "0990000001", PrimeContext.READY, UUID.randomUUID(), clock.instant());
        when(primeContext.phone("user-1", PointsAward.RULE)).thenReturn(new Phone(null, PrimeContext.SEVERAL));
        when(payoutNumberRepository.findByDeclarantId("user-1")).thenReturn(Optional.of(current));
        var command = new RecordPayoutCommand(primeContext, payoutNumberRepository, clock);

        var recorded = command.execute("user-1", UUID.randomUUID());

        assertThat(recorded).contains(current.getId());
        assertThat(current.getPhone()).isNull();
        assertThat(current.getState()).isEqualTo(PrimeContext.SEVERAL);
    }

    @Test
    void keepsTheSameSinglePhoneWithoutANewRecord() {
        var current = new PayoutNumber(UUID.randomUUID(), "user-1", "0990000001", PrimeContext.READY, UUID.randomUUID(), clock.instant());
        when(primeContext.phone("user-1", PointsAward.RULE)).thenReturn(new Phone("0990000001", PrimeContext.READY));
        when(payoutNumberRepository.findByDeclarantId("user-1")).thenReturn(Optional.of(current));
        var command = new RecordPayoutCommand(primeContext, payoutNumberRepository, clock);

        assertThat(command.execute("user-1", UUID.randomUUID())).isEmpty();
    }
}
