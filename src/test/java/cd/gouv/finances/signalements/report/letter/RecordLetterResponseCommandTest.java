package cd.gouv.finances.signalements.report.letter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.Letter;
import cd.gouv.finances.signalements.report.entity.LetterResponse;
import cd.gouv.finances.signalements.report.repository.LetterRepository;
import cd.gouv.finances.signalements.report.repository.LetterResponseRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordLetterResponseCommandTest {

    @Mock
    private LetterRepository letterRepository;

    @Mock
    private LetterResponseRepository letterResponseRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T22:00:00Z"), ZoneOffset.UTC);
    private final byte[] pdf = "%PDF-1.7".getBytes();

    @Test
    void aValidatedLetterKeepsItsStatusWhenAResponseIsRecorded() {
        var letter = validated();
        when(letterRepository.findById(letter.getId())).thenReturn(Optional.of(letter));
        when(letterResponseRepository.findByLetterIdAndReferenceCode(letter.getId(), "REP-1")).thenReturn(Optional.empty());
        var command = command();

        var created = command.execute(letter.getId(), "2026-11-01", "  Direction provinciale  ", "REP-1", "Conclusion reçue.", pdf, UUID.randomUUID());

        assertThat(created).isTrue();
        assertThat(letter.getStatus()).isEqualTo(Letter.VALIDATED);
        var saved = ArgumentCaptor.forClass(LetterResponse.class);
        verify(letterResponseRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getService()).isEqualTo("Direction provinciale");
        assertThat(saved.getValue().getContentType()).isEqualTo("application/pdf");
    }

    @Test
    void aDraftCannotReceiveAResponse() {
        var letter = new Letter(UUID.randomUUID(), "CO-2026-000001", LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-08"), UUID.randomUUID(), clock.instant());
        when(letterRepository.findById(letter.getId())).thenReturn(Optional.of(letter));
        var command = command();

        assertThatThrownBy(() -> command.execute(letter.getId(), "2026-11-01", "Direction", "REP-1", "Conclusion.", pdf, UUID.randomUUID()))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("response-letter");
        verify(letterResponseRepository, never()).saveAndFlush(any());
        assertThat(letter.getStatus()).isEqualTo(Letter.DRAFT);
    }

    private RecordLetterResponseCommand command() {
        return new RecordLetterResponseCommand(letterRepository, letterResponseRepository, clock);
    }

    private Letter validated() {
        var letter = new Letter(UUID.randomUUID(), "CO-2026-000001", LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-08"), UUID.randomUUID(), clock.instant());
        letter.decide(Letter.VALIDATED, null, null, UUID.randomUUID(), clock.instant());
        return letter;
    }
}
