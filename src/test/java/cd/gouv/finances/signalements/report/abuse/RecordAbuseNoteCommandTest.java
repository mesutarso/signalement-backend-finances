package cd.gouv.finances.signalements.report.abuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.AbuseNote;
import cd.gouv.finances.signalements.report.repository.AbuseNoteRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class RecordAbuseNoteCommandTest {

    @Mock
    private AbuseNoteRepository abuseNoteRepository;

    @Mock
    private JdbcTemplate jdbc;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T22:00:00Z"), ZoneOffset.UTC);

    @Test
    void recordsAReadingWithoutCallingItFraud() {
        when(jdbc.queryForObject(contains("count(distinct report_id)"), eq(Integer.class), eq("abc"))).thenReturn(2);
        when(jdbc.queryForObject(contains("limit 1"), eq(Integer.class), eq("abc"), eq("noted"), eq("Image revue."))).thenReturn(0);
        var command = new RecordAbuseNoteCommand(abuseNoteRepository, jdbc, clock);

        command.execute("abc", "noted", " Image revue. ", UUID.randomUUID());

        var saved = ArgumentCaptor.forClass(AbuseNote.class);
        verify(abuseNoteRepository).save(saved.capture());
        assertThat(saved.getValue().getDecision()).isEqualTo("noted");
        assertThat(saved.getValue().getReason()).isEqualTo("Image revue.");
    }

    @Test
    void anUnknownImageIsRejected() {
        when(jdbc.queryForObject(contains("count(distinct report_id)"), eq(Integer.class), eq("abc"))).thenReturn(1);
        var command = new RecordAbuseNoteCommand(abuseNoteRepository, jdbc, clock);

        assertThatThrownBy(() -> command.execute("abc", "dismissed", "Rien à suivre.", UUID.randomUUID()))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("abuse-absent");
        verify(abuseNoteRepository, never()).save(any());
    }
}
