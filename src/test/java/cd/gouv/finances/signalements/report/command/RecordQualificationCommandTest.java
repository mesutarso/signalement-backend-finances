package cd.gouv.finances.signalements.report.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cd.gouv.finances.signalements.report.entity.Qualification;
import cd.gouv.finances.signalements.report.repository.QualificationRepository;
import cd.gouv.finances.signalements.report.repository.ReportRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordQualificationCommandTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private QualificationRepository qualificationRepository;

    @Mock
    private cd.gouv.finances.signalements.report.points.PointsContext pointsContext;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T22:00:00Z"), ZoneOffset.UTC);

    @Test
    void recordsTheFindingWithoutChangingTheReport() {
        var reportId = UUID.randomUUID();
        when(reportRepository.existsById(reportId)).thenReturn(true);
        when(qualificationRepository.findByReportIdOrderByRecordedAtDesc(reportId)).thenReturn(List.of());
        var command = new RecordQualificationCommand(reportRepository, qualificationRepository, pointsContext, clock);

        command.execute(reportId, request("confirm", "non-normalized", "photo", "La facture n'est pas normalisée.", "A0001"), UUID.randomUUID());

        var saved = ArgumentCaptor.forClass(Qualification.class);
        verify(qualificationRepository).save(saved.capture());
        assertThat(saved.getValue().getFinding()).isEqualTo("non-normalized");
        assertThat(saved.getValue().getSellerTaxId()).isEqualTo("A0001");
        verify(reportRepository, never()).save(any());
    }

    @Test
    void repeatsTheSameDecisionOnce() {
        var reportId = UUID.randomUUID();
        var existing = new Qualification(
                UUID.randomUUID(),
                reportId,
                "reject",
                null,
                null,
                "Pas de manquement visible.",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                clock.instant());
        when(reportRepository.existsById(reportId)).thenReturn(true);
        when(qualificationRepository.findByReportIdOrderByRecordedAtDesc(reportId)).thenReturn(List.of(existing));
        var command = new RecordQualificationCommand(reportRepository, qualificationRepository, pointsContext, clock);

        command.execute(reportId, request("reject", null, null, "Pas de manquement visible.", null), UUID.randomUUID());

        verify(qualificationRepository, never()).save(any());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void keepsTheSellerWhileWaitingAndAfterTheDgiConfirmsARefusal() {
        var reportId = UUID.randomUUID();
        var actor = UUID.randomUUID();
        when(reportRepository.existsById(reportId)).thenReturn(true);
        var waiting = new Qualification(UUID.randomUUID(), reportId, "wait", "refusal", "pending", "Attente DGI",
                "Vendeur", "A0001", null, null, null, null, "Confirmation DGI", null, actor, clock.instant());
        when(qualificationRepository.findByReportIdOrderByRecordedAtDesc(reportId)).thenReturn(List.of(waiting));
        var command = new RecordQualificationCommand(reportRepository, qualificationRepository, pointsContext, clock);

        command.execute(reportId, new RecordQualificationCommand.QualificationRequest("confirm", "refusal", "authority",
                "Refus confirmé", null, null, null, null, null, null, null, "DGI-1"), actor);

        var saved = ArgumentCaptor.forClass(Qualification.class);
        verify(qualificationRepository).save(saved.capture());
        assertThat(saved.getValue().getSellerTaxId()).isEqualTo("A0001");
        assertThat(saved.getValue().getSellerName()).isEqualTo("Vendeur");
        assertThat(saved.getValue().getDgiReference()).isEqualTo("DGI-1");
    }

    @Test
    void requiresTracedReversalBeforeReplacingACreditedFinding() {
        var id = UUID.randomUUID();
        when(reportRepository.existsById(id)).thenReturn(true);
        when(qualificationRepository.findByReportIdOrderByRecordedAtDesc(id)).thenReturn(List.of());
        when(qualificationRepository.hasCreditedPoints(id)).thenReturn(true);
        var command = new RecordQualificationCommand(reportRepository, qualificationRepository, pointsContext, clock);

        assertThatThrownBy(() -> command.execute(id, request("reject", null, null, "À corriger", null), UUID.randomUUID()))
                .isInstanceOf(cd.gouv.finances.signalements.report.ReportRejected.class)
                .hasMessage("qualification-credited");
        verify(qualificationRepository, never()).save(any());
    }

    private static RecordQualificationCommand.QualificationRequest request(
            String decision, String finding, String proof, String justification, String taxId) {
        return new RecordQualificationCommand.QualificationRequest(
                decision, finding, proof, justification, null, taxId, null, null, null, null, null, null);
    }
}
