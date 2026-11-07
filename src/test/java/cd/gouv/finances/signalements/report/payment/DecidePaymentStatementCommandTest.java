package cd.gouv.finances.signalements.report.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.PaymentStatement;
import cd.gouv.finances.signalements.report.entity.PaymentStatementLine;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DecidePaymentStatementCommandTest {

    @Mock
    private PaymentStatementRepository paymentStatementRepository;
    @Mock
    private PaymentStatementLineRepository paymentStatementLineRepository;

    private DecidePaymentStatementCommand command;
    private PaymentStatement statement;
    private PaymentStatementLine line;
    private UUID supervisor;
    private UUID cellule;

    @BeforeEach
    void setUp() {
        command = new DecidePaymentStatementCommand(
                paymentStatementRepository,
                paymentStatementLineRepository,
                Clock.fixed(Instant.parse("2026-11-06T10:00:00Z"), ZoneOffset.UTC));
        supervisor = UUID.randomUUID();
        cellule = UUID.randomUUID();
        statement = new PaymentStatement(UUID.randomUUID(), "EP-2026-000001", UUID.randomUUID(), Instant.parse("2026-11-06T08:00:00Z"));
        line = new PaymentStatementLine(UUID.randomUUID(), statement.getId(), UUID.randomUUID(), 30, 10000, "0810000000");
        when(paymentStatementRepository.findById(statement.getId())).thenReturn(Optional.of(statement));
    }

    @Test
    void controlMovesADraftForward() {
        var action = command.control(statement.getId(), "accept", null, supervisor);

        assertThat(action).isEqualTo("payment-control");
        assertThat(statement.getStatus()).isEqualTo(PaymentStatement.CONTROLLED);
        assertThat(statement.getDueOn()).isNull();
    }

    @Test
    void visaByTheControllerIsRefused() {
        statement.control(supervisor, Instant.parse("2026-11-06T09:00:00Z"));

        assertThatThrownBy(() -> command.visa(statement.getId(), "accept", null, supervisor))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("payment-separation");
        assertThat(statement.getStatus()).isEqualTo(PaymentStatement.CONTROLLED);
    }

    @Test
    void authorizationSetsTheWeekdayDeadlineWithoutAPayment() {
        statement.control(supervisor, Instant.parse("2026-11-06T09:00:00Z"));
        statement.visa(cellule, Instant.parse("2026-11-06T09:30:00Z"));

        var action = command.authorize(statement.getId(), "accept", null, UUID.randomUUID());

        assertThat(action).isEqualTo("payment-authorization");
        assertThat(statement.getStatus()).isEqualTo(PaymentStatement.AUTHORIZED);
        assertThat(statement.getDueOn()).isEqualTo(LocalDate.parse("2026-11-20"));
    }

    @Test
    void returnReleasesTheLines() {
        statement.control(supervisor, Instant.parse("2026-11-06T09:00:00Z"));
        when(paymentStatementLineRepository.findByStatementId(statement.getId())).thenReturn(List.of(line));

        var action = command.visa(statement.getId(), "return", "  Montant à revoir  ", cellule);

        assertThat(action).isEqualTo("payment-return");
        assertThat(statement.getStatus()).isEqualTo(PaymentStatement.RETURNED);
        assertThat(statement.getReason()).isEqualTo("Montant à revoir");
        assertThat(line.isReleased()).isTrue();
    }
}
