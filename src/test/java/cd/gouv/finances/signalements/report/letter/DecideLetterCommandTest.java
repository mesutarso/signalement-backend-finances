package cd.gouv.finances.signalements.report.letter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.Letter;
import cd.gouv.finances.signalements.report.repository.LetterRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DecideLetterCommandTest {

    @Mock
    private LetterRepository letterRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T22:00:00Z"), ZoneOffset.UTC);

    @Test
    void validationKeepsTheLinesAndDoesNotRequireARecipient() {
        var letter = draft();
        whenFound(letter);
        var command = new DecideLetterCommand(letterRepository, clock);

        var decided = command.execute(letter.getId(), "validated", "  ", "motif ignoré", UUID.randomUUID());

        assertThat(decided).isTrue();
        assertThat(letter.getStatus()).isEqualTo("validated");
        assertThat(letter.getRecipient()).isNull();
        assertThat(letter.getReason()).isNull();
        verify(letterRepository).save(letter);
    }

    @Test
    void aReturnWithoutAReasonIsRefused() {
        var letter = draft();
        whenFound(letter);
        var command = new DecideLetterCommand(letterRepository, clock);

        assertThatThrownBy(() -> command.execute(letter.getId(), "returned", null, "  ", UUID.randomUUID()))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("letter-reason");
        assertThat(letter.getStatus()).isEqualTo("draft");
    }

    private void whenFound(Letter letter) {
        org.mockito.Mockito.when(letterRepository.findById(letter.getId())).thenReturn(Optional.of(letter));
    }

    private Letter draft() {
        return new Letter(UUID.randomUUID(), "CO-2026-000001", LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-08"), UUID.randomUUID(), clock.instant());
    }
}
