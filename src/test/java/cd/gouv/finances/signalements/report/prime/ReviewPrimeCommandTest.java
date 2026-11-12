package cd.gouv.finances.signalements.report.prime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.PayoutNumber;
import cd.gouv.finances.signalements.report.entity.Prime;
import cd.gouv.finances.signalements.report.entity.PrimeReview;
import cd.gouv.finances.signalements.report.repository.PayoutNumberRepository;
import cd.gouv.finances.signalements.report.repository.PrimeRepository;
import cd.gouv.finances.signalements.report.repository.PrimeReviewRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReviewPrimeCommandTest {

    @Mock
    private PrimeRepository primeRepository;

    @Mock
    private PrimeReviewRepository primeReviewRepository;

    @Mock
    private PayoutNumberRepository payoutNumberRepository;

    @Mock
    private RecordPayoutCommand recordPayoutCommand;

    @Mock
    private PrimeGuard primeGuard;

    @Mock
    private BeneficiaryRegistry beneficiaries;

    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-07T01:00:00Z"), ZoneOffset.UTC);

    @Test
    void approvesWhenThePayoutNumberIsTheDeclarantPhone() {
        var prime = proposed();
        var reviewer = UUID.randomUUID();
        when(primeRepository.findById(prime.getId())).thenReturn(Optional.of(prime));
        when(recordPayoutCommand.execute("user-1", reviewer)).thenReturn(Optional.empty());
        when(payoutNumberRepository.findByDeclarantId("user-1")).thenReturn(Optional.of(payout(prime.getPhone())));
        var command = command();

        var outcome = command.execute(prime.getId(), "approved", null, reviewer);

        var saved = ArgumentCaptor.forClass(PrimeReview.class);
        verify(primeReviewRepository).save(saved.capture());
        assertThat(outcome.action()).isEqualTo("reward-approval");
        assertThat(prime.getStatus()).isEqualTo(Prime.APPROVED);
        assertThat(saved.getValue().getReason()).isNull();
    }

    @Test
    void refusesApprovalWhenTheDeclarantHasSeveralPhones() {
        var prime = proposed();
        var reviewer = UUID.randomUUID();
        when(primeRepository.findById(prime.getId())).thenReturn(Optional.of(prime));
        when(recordPayoutCommand.execute("user-1", reviewer)).thenReturn(Optional.empty());
        when(payoutNumberRepository.findByDeclarantId("user-1")).thenReturn(Optional.of(payout(null, PrimeContext.SEVERAL)));
        var command = command();

        assertThatThrownBy(() -> command.execute(prime.getId(), "approved", null, reviewer))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("prime-phone");
        verify(primeReviewRepository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(prime.getStatus()).isEqualTo(Prime.PROPOSED);
    }

    @Test
    void refusesApprovalByTheAgentWhoRetainedTheFinding() {
        var prime = proposed();
        var agent = UUID.randomUUID();
        when(primeRepository.findById(prime.getId())).thenReturn(Optional.of(prime));
        when(recordPayoutCommand.execute("user-1", agent)).thenReturn(Optional.empty());
        when(payoutNumberRepository.findByDeclarantId("user-1")).thenReturn(Optional.of(payout(prime.getPhone())));
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(UUID.class), org.mockito.ArgumentMatchers.eq(prime.getReportId()))).thenReturn(List.of(agent));
        var command = command();

        assertThatThrownBy(() -> command.execute(prime.getId(), "approved", null, agent))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("prime-separation");
        verify(primeReviewRepository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(prime.getStatus()).isEqualTo(Prime.PROPOSED);
    }

    @Test
    void returnsWithAReasonWithoutAuthorizingPayment() {
        var prime = proposed();
        var reviewer = UUID.randomUUID();
        when(primeRepository.findById(prime.getId())).thenReturn(Optional.of(prime));
        when(recordPayoutCommand.execute("user-1", reviewer)).thenReturn(Optional.empty());
        var command = command();

        var outcome = command.execute(prime.getId(), "returned", "  Le numéro doit être revu.  ", reviewer);

        assertThat(outcome.action()).isEqualTo("reward-return");
        assertThat(prime.getStatus()).isEqualTo(Prime.RETURNED);
        var saved = ArgumentCaptor.forClass(PrimeReview.class);
        verify(primeReviewRepository).save(saved.capture());
        assertThat(saved.getValue().getReason()).isEqualTo("Le numéro doit être revu.");
    }

    private ReviewPrimeCommand command() {
        return new ReviewPrimeCommand(primeRepository, primeReviewRepository, payoutNumberRepository, recordPayoutCommand, primeGuard, beneficiaries, clock, org.mockito.Mockito.mock(cd.gouv.finances.signalements.report.validation.RewardPaymentGuard.class), jdbc);
    }

    @Test
    void approvesAnAutomaticallyValidatedRewardWithoutApplyingHistoricalPointForfeiture() {
        var prime = new Prime(UUID.randomUUID(), "user-1", "0990000001", "fixed-reward", 0, 5000, UUID.randomUUID(), clock.instant());
        ReflectionTestUtils.setField(prime, "reportId", UUID.randomUUID());
        var reviewer = UUID.randomUUID();
        when(primeRepository.findById(prime.getId())).thenReturn(Optional.of(prime));
        when(recordPayoutCommand.execute("user-1", reviewer)).thenReturn(Optional.empty());
        when(payoutNumberRepository.findByDeclarantId("user-1")).thenReturn(Optional.of(payout(prime.getPhone())));

        command().execute(prime.getId(), "approved", null, reviewer);

        assertThat(prime.getStatus()).isEqualTo(Prime.APPROVED);
        verify(beneficiaries).requireCapacity(prime);
    }

    @Test
    void refusesARetiredTierEvenWhenItsIdentifierIsKnown() {
        var legacy = new Prime(UUID.randomUUID(), "user-1", "0990000001", "old-rule", 30, 10000, UUID.randomUUID(), clock.instant());
        when(primeRepository.findById(legacy.getId())).thenReturn(Optional.of(legacy));
        assertThatThrownBy(() -> command().execute(legacy.getId(), "approved", null, UUID.randomUUID()))
                .isInstanceOf(ReportRejected.class).hasMessage("prime-absent");
        verify(primeReviewRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private Prime proposed() {
        var reward = new Prime(UUID.randomUUID(), "user-1", "0990000001", "fixed-reward", 0, 5000, UUID.randomUUID(), clock.instant());
        ReflectionTestUtils.setField(reward, "reportId", UUID.randomUUID());
        return reward;
    }

    private PayoutNumber payout(String phone) {
        return payout(phone, PrimeContext.READY);
    }

    private PayoutNumber payout(String phone, String state) {
        return new PayoutNumber(UUID.randomUUID(), "user-1", phone, state, UUID.randomUUID(), clock.instant());
    }
}
