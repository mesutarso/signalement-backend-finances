package cd.gouv.finances.signalements.report.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.PaymentStatement;
import cd.gouv.finances.signalements.report.entity.PaymentStatementExclusion;
import cd.gouv.finances.signalements.report.entity.PaymentStatementLine;
import cd.gouv.finances.signalements.report.repository.PaymentStatementExclusionRepository;
import cd.gouv.finances.signalements.report.repository.PaymentStatementLineRepository;
import cd.gouv.finances.signalements.report.repository.PaymentStatementRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreparePaymentStatementCommandTest {

    @Mock
    private PaymentStatementRepository paymentStatementRepository;
    @Mock
    private PaymentStatementLineRepository paymentStatementLineRepository;
    @Mock
    private PaymentStatementExclusionRepository paymentStatementExclusionRepository;
    @Mock
    private PaymentSourceLoader paymentSourceLoader;

    private PreparePaymentStatementCommand command;

    @BeforeEach
    void setUp() {
        command = new PreparePaymentStatementCommand(
                paymentStatementRepository,
                paymentStatementLineRepository,
                paymentStatementExclusionRepository,
                paymentSourceLoader,
                Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void savesNothingWhenNoPrimeIsReady() {
        when(paymentStatementRepository.findFirstByStatusIn(any())).thenReturn(Optional.empty());
        when(paymentSourceLoader.candidates()).thenReturn(List.of());

        assertThatThrownBy(() -> command.execute(UUID.randomUUID()))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("payment-empty");

        verify(paymentStatementRepository, never()).save(any());
    }

    @Test
    void keepsAReadyPrimeAndExcludesSeveralNumbers() {
        var ready = UUID.randomUUID();
        var several = UUID.randomUUID();
        when(paymentStatementRepository.findFirstByStatusIn(any())).thenReturn(Optional.empty());
        when(paymentStatementRepository.nextNumber()).thenReturn(7L);
        when(paymentSourceLoader.candidates()).thenReturn(List.of(
                new PaymentSourceLoader.Candidate(ready, 30, 10000, "0810000000", "ready", "0810000000", false),
                new PaymentSourceLoader.Candidate(several, 60, 20000, "0820000000", "several", null, false)));

        var prepared = command.execute(UUID.randomUUID());

        assertThat(prepared.created()).isTrue();
        var line = ArgumentCaptor.forClass(PaymentStatementLine.class);
        verify(paymentStatementLineRepository).save(line.capture());
        assertThat(line.getValue().getPrimeId()).isEqualTo(ready);
        assertThat(line.getValue().isReleased()).isFalse();
        var exclusion = ArgumentCaptor.forClass(PaymentStatementExclusion.class);
        verify(paymentStatementExclusionRepository).save(exclusion.capture());
        assertThat(exclusion.getValue().getPrimeId()).isEqualTo(several);
        assertThat(exclusion.getValue().getReason()).isEqualTo("Le déclarant a plusieurs numéros.");
    }

    @Test
    void refusesWhenAStatementIsAlreadyOpen() {
        when(paymentStatementRepository.findFirstByStatusIn(any())).thenReturn(Optional.of(
                new PaymentStatement(UUID.randomUUID(), "EP-2026-000001", UUID.randomUUID(), Instant.parse("2026-11-07T09:00:00Z"))));

        assertThatThrownBy(() -> command.execute(UUID.randomUUID()))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("payment-open");

        verify(paymentSourceLoader, never()).candidates();
    }
}
