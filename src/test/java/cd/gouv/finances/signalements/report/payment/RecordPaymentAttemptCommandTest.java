package cd.gouv.finances.signalements.report.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.PaymentAttempt;
import cd.gouv.finances.signalements.report.entity.PaymentStatement;
import cd.gouv.finances.signalements.report.entity.PaymentStatementLine;
import cd.gouv.finances.signalements.report.repository.PaymentAttemptRepository;
import cd.gouv.finances.signalements.report.repository.PaymentStatementLineRepository;
import cd.gouv.finances.signalements.report.repository.PaymentStatementRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
class RecordPaymentAttemptCommandTest {

    @Mock
    private PaymentStatementRepository paymentStatementRepository;
    @Mock
    private PaymentStatementLineRepository paymentStatementLineRepository;
    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;

    private RecordPaymentAttemptCommand command;
    private PaymentStatement statement;
    private PaymentStatementLine line;
    private UUID paymaster;

    @BeforeEach
    void setUp() {
        command = new RecordPaymentAttemptCommand(
                paymentStatementRepository,
                paymentStatementLineRepository,
                paymentAttemptRepository,
                Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC), org.mockito.Mockito.mock(cd.gouv.finances.signalements.report.validation.RewardPaymentGuard.class));
        var cabinet = UUID.randomUUID();
        paymaster = UUID.randomUUID();
        statement = new PaymentStatement(UUID.randomUUID(), "EP-2026-000001", UUID.randomUUID(), Instant.parse("2026-11-06T08:00:00Z"));
        statement.control(UUID.randomUUID(), Instant.parse("2026-11-06T09:00:00Z"));
        statement.visa(UUID.randomUUID(), Instant.parse("2026-11-06T09:30:00Z"));
        statement.authorize(cabinet, Instant.parse("2026-11-06T10:00:00Z"), LocalDate.parse("2026-11-20"));
        line = new PaymentStatementLine(UUID.randomUUID(), statement.getId(), UUID.randomUUID(), 30, 10000, "0810000000");
        when(paymentStatementRepository.findById(statement.getId())).thenReturn(Optional.of(statement));
    }

    private void givenLine() {
        when(paymentStatementLineRepository.findById(line.getId())).thenReturn(Optional.of(line));
    }

    @Test
    void recordsAPaidResultWithItsReference() {
        givenLine();
        when(paymentAttemptRepository.findByLineIdOrderByRecordedAtAsc(line.getId())).thenReturn(List.of());
        when(paymentAttemptRepository.nextNumber()).thenReturn(7L);

        var action = command.execute(statement.getId(), line.getId(), "airtel", "paid", null, paymaster);

        assertThat(action).isEqualTo("payment-paid");
        var attempt = ArgumentCaptor.forClass(PaymentAttempt.class);
        verify(paymentAttemptRepository).save(attempt.capture());
        assertThat(attempt.getValue().getOperator()).isEqualTo(PaymentSimulation.AIRTEL);
        assertThat(attempt.getValue().getResult()).isEqualTo(PaymentAttempt.PAID);
        assertThat(attempt.getValue().getReferenceCode()).isEqualTo("SIM-AIRTEL-000007");
        assertThat(attempt.getValue().getReason()).isNull();
    }

    @Test
    void refusesASecondPayment() {
        givenLine();
        when(paymentAttemptRepository.findByLineIdOrderByRecordedAtAsc(line.getId())).thenReturn(List.of(paid()));

        assertThatThrownBy(() -> command.execute(statement.getId(), line.getId(), "m-pesa", "paid", null, paymaster))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("payment-paid");
        verify(paymentAttemptRepository, never()).save(any());
    }

    @Test
    void asksForTheUncertainResultBeforeAnotherAttempt() {
        givenLine();
        when(paymentAttemptRepository.findByLineIdOrderByRecordedAtAsc(line.getId())).thenReturn(List.of(unknown()));

        assertThatThrownBy(() -> command.execute(statement.getId(), line.getId(), "orange", "unknown", "Toujours sans retour", paymaster))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("payment-uncertain");
        verify(paymentAttemptRepository, never()).save(any());
    }

    @Test
    void confirmsAnUncertainResultAsPaid() {
        givenLine();
        when(paymentAttemptRepository.findByLineIdOrderByRecordedAtAsc(line.getId())).thenReturn(List.of(unknown()));

        when(paymentAttemptRepository.nextNumber()).thenReturn(8L);

        var action = command.execute(statement.getId(), line.getId(), "afrimoney", "paid", null, paymaster);

        assertThat(action).isEqualTo("payment-paid");
    }

    @Test
    void allowsAnotherAttemptAfterAFailure() {
        givenLine();
        when(paymentAttemptRepository.findByLineIdOrderByRecordedAtAsc(line.getId())).thenReturn(List.of(failed()));

        when(paymentAttemptRepository.nextNumber()).thenReturn(9L);

        var action = command.execute(statement.getId(), line.getId(), "orange", "failed", "Opérateur injoignable", paymaster);

        assertThat(action).isEqualTo("payment-failed");
    }

    @Test
    void refusesTheOfficeThatAuthorizedTheStatement() {
        assertThatThrownBy(() -> command.execute(statement.getId(), line.getId(), "airtel", "paid", null, statement.getAuthorizedBy()))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("payment-separation");
        verify(paymentAttemptRepository, never()).save(any());
    }

    @Test
    void refusesAnUnknownPaymentChannel() {
        assertThatThrownBy(() -> command.execute(statement.getId(), line.getId(), "banque", "paid", null, paymaster))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("payment-operator");
        verify(paymentAttemptRepository, never()).save(any());
    }

    private PaymentAttempt paid() {
        return new PaymentAttempt(UUID.randomUUID(), statement.getId(), line.getId(), PaymentSimulation.AIRTEL, PaymentAttempt.PAID, "SIM-AIRTEL-000001", null, paymaster, Instant.parse("2026-11-20T08:00:00Z"));
    }

    private PaymentAttempt failed() {
        return new PaymentAttempt(UUID.randomUUID(), statement.getId(), line.getId(), PaymentSimulation.ORANGE, PaymentAttempt.FAILED, "SIM-ORANGE-000002", "Rejeté", paymaster, Instant.parse("2026-11-20T08:00:00Z"));
    }

    private PaymentAttempt unknown() {
        return new PaymentAttempt(UUID.randomUUID(), statement.getId(), line.getId(), PaymentSimulation.M_PESA, PaymentAttempt.UNKNOWN, "SIM-MPESA-000003", "Sans retour", paymaster, Instant.parse("2026-11-20T08:00:00Z"));
    }
}
