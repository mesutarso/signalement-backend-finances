package cd.gouv.finances.signalements.report.letter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.Letter;
import cd.gouv.finances.signalements.report.repository.LetterLineRepository;
import cd.gouv.finances.signalements.report.repository.LetterRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrepareLetterCommandTest {

    @Mock
    private LetterRepository letterRepository;

    @Mock
    private LetterLineRepository letterLineRepository;

    @Mock
    private LetterSourceLoader letterSourceLoader;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T22:00:00Z"), ZoneOffset.UTC);

    @Test
    void anEmptyWeekDoesNotCreateALetter() {
        when(letterRepository.findFirstByPeriodStartAndStatus(LocalDate.parse("2026-11-02"), Letter.DRAFT)).thenReturn(Optional.empty());
        when(letterRepository.findFirstByPeriodStartAndStatus(LocalDate.parse("2026-11-02"), Letter.VALIDATED)).thenReturn(Optional.empty());
        when(letterSourceLoader.findings()).thenReturn(List.of());
        var command = command();

        assertThatThrownBy(() -> command.execute(UUID.randomUUID()))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("letter-empty");
        verify(letterRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void theDraftOfTheWeekIsReused() {
        var draft = new Letter(UUID.randomUUID(), "CO-2026-000001", LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-08"), UUID.randomUUID(), clock.instant());
        when(letterRepository.findFirstByPeriodStartAndStatus(LocalDate.parse("2026-11-02"), Letter.DRAFT)).thenReturn(Optional.of(draft));
        var command = command();

        var prepared = command.execute(UUID.randomUUID());

        assertThat(prepared.created()).isFalse();
        assertThat(prepared.id()).isEqualTo(draft.getId());
        verify(letterSourceLoader, never()).findings();
    }

    private PrepareLetterCommand command() {
        return new PrepareLetterCommand(letterRepository, letterLineRepository, letterSourceLoader, clock);
    }
}
