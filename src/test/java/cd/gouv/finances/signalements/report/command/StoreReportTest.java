package cd.gouv.finances.signalements.report.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import cd.gouv.finances.signalements.report.entity.Report;
import cd.gouv.finances.signalements.report.repository.ReportRepository;
import cd.gouv.finances.signalements.report.source.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class StoreReportTest {
    @Test
    void replayDoesNotDuplicateTheReportOrItsPhoto() {
        var repository = mock(ReportRepository.class);
        var saved = new AtomicReference<Report>();
        when(repository.findBySourceAndExternalId(ReportSource.NAME, "report-1"))
                .thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        when(repository.save(any())).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            saved.set(report);
            return report;
        });
        var command = new StoreReport(repository, Clock.systemUTC());
        var incoming = new SourcedReport("report-1", LocalDateTime.parse("2026-11-06T08:00:00"),
                "Titre", "Description", "Déclarant", "test@example.com", "123", "user-1",
                List.of(new SourcedPhoto("photo-1", "image/png", new byte[] {1})));
        var first = command.execute(incoming);
        var replay = command.execute(incoming);
        assertThat(first.created()).isTrue();
        assertThat(replay.created()).isFalse();
        assertThat(replay.createdPhotos()).isZero();
        assertThat(saved.get().getPhotos()).hasSize(1);
        verify(repository, times(1)).save(any());
    }
}
