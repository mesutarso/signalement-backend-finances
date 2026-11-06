package cd.gouv.finances.signalements.report.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cd.gouv.finances.signalements.report.repository.ReportImportRepository;
import cd.gouv.finances.signalements.report.source.ReportSource;
import cd.gouv.finances.signalements.report.source.SourcedPage;
import cd.gouv.finances.signalements.report.source.SourcedPhoto;
import cd.gouv.finances.signalements.report.source.SourcedReport;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImportReportsCommandTest {

    @Mock
    private ReportSource reportSource;

    @Mock
    private StoreReport storeReport;

    @Mock
    private ReportImportRepository reportImportRepository;

    @Test
    void replaySkipsAReportAlreadyStored() {
        var report = new SourcedReport(
                "signalement-001",
                LocalDateTime.parse("2026-10-19T10:30:00"),
                "Facture non délivrée",
                "Le commerçant n'a pas remis de facture.",
                "Claire Mbuyi",
                "claire.mbuyi@exemple.cd",
                "+243810000000",
                "utilisateur-001",
                List.of(new SourcedPhoto("image-001", "image/png", new byte[] {1})));
        when(reportSource.read(any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(new SourcedPage(List.of(report), true));
        when(storeReport.execute(report)).thenReturn(new StoreReport.Outcome(true, 1), new StoreReport.Outcome(false, 0));
        when(reportImportRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var command = new ImportReportsCommand(
                reportSource,
                storeReport,
                reportImportRepository,
                Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC));

        var first = command.execute(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-11-01"));
        var second = command.execute(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-11-01"));

        assertThat(first.createdReports()).isEqualTo(1);
        assertThat(first.createdPhotos()).isEqualTo(1);
        assertThat(second.skippedReports()).isEqualTo(1);
        assertThat(second.createdReports()).isZero();
        assertThat(second.createdPhotos()).isZero();
    }
    @Test
    void recordsSourceFailuresForMonitoring() {
        when(reportImportRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reportSource.read(any(), any(), any(Integer.class), any(Integer.class)))
                .thenThrow(new cd.gouv.finances.signalements.report.ReportRejected("source", 502));
        var command = new ImportReportsCommand(reportSource, storeReport, reportImportRepository,
                Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC));
        var result = command.execute(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-06"));
        assertThat(result.status()).isEqualTo("failed");
        assertThat(result.errorCode()).isEqualTo("source");
        assertThat(result.finishedAt()).isNotNull();
        assertThat(result.periodStart()).isEqualTo(LocalDate.parse("2026-11-01"));
    }

    @Test
    void refusesASecondActiveImport() {
        when(reportImportRepository.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("active"));
        var command = new ImportReportsCommand(reportSource, storeReport, reportImportRepository, Clock.systemUTC());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> command.prepare(LocalDate.now(), LocalDate.now(), "manual"))
                .isInstanceOf(cd.gouv.finances.signalements.report.ReportRejected.class).hasMessage("import-running");
        org.mockito.Mockito.verifyNoInteractions(reportSource);
    }
}
