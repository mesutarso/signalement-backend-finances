package cd.gouv.finances.signalements.report.taxpayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.Taxpayer;
import cd.gouv.finances.signalements.report.repository.TaxpayerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerifyTaxpayerCommandTest {

    @Mock
    private TaxpayerRepository taxpayerRepository;

    @Test
    void leavesTheTaxpayerUnverifiedWhenTheRegistryIsUnavailable() {
        var taxpayer = new Taxpayer(UUID.randomUUID(), "A1234567B", "Société", Instant.parse("2026-11-07T10:00:00Z"));
        when(taxpayerRepository.findById(taxpayer.getId())).thenReturn(Optional.of(taxpayer));
        var command = new VerifyTaxpayerCommand(
                taxpayerRepository,
                new TaxpayerVerification(),
                Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> command.execute(taxpayer.getId()))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("taxpayer-unavailable");
        assertThat(taxpayer.isVerified()).isFalse();
    }
}
