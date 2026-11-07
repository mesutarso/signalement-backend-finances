package cd.gouv.finances.signalements.report.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cd.gouv.finances.signalements.ocr.dto.ReadingResponse;
import cd.gouv.finances.signalements.ocr.query.ReadDocumentReadingsQuery;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.PhotoRequest;
import cd.gouv.finances.signalements.report.repository.PhotoRequestRepository;
import cd.gouv.finances.signalements.report.repository.ReportPhotoRepository;
import cd.gouv.finances.signalements.report.repository.ReportRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestPhotoCommandTest {

    @Mock
    private ReportPhotoRepository reportPhotoRepository;

    @Mock
    private PhotoRequestRepository photoRequestRepository;

    @Mock
    private ReadDocumentReadingsQuery readDocumentReadingsQuery;

    @Mock
    private ReportRepository reportRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T20:00:00Z"), ZoneOffset.UTC);

    @Test
    void keepsTheOriginalReportAndRecordsAnOpenRequest() {
        var reportId = UUID.randomUUID();
        var photoId = UUID.randomUUID();
        var actorId = UUID.randomUUID();
        when(reportPhotoRepository.existsByIdAndReport_Id(photoId, reportId)).thenReturn(true);
        when(photoRequestRepository.findFirstByPhotoIdAndStatus(photoId, PhotoRequest.OPEN)).thenReturn(Optional.empty());
        when(readDocumentReadingsQuery.latest(List.of(photoId.toString()))).thenReturn(Map.of(
                photoId.toString(),
                new ReadingResponse(
                        UUID.randomUUID(),
                        photoId.toString(),
                        "READ",
                        "UNREADABLE",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null)));
        when(photoRequestRepository.nextNumber()).thenReturn(12L);
        when(photoRequestRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var command = new RequestPhotoCommand(
                reportPhotoRepository, photoRequestRepository, readDocumentReadingsQuery, clock);

        var created = command.execute(reportId, photoId, "  La photo est floue.  ", actorId);

        assertThat(created.referenceCode()).isEqualTo("NP-2026-000012");
        assertThat(created.basis()).isEqualTo(PhotoRequest.READING);
        assertThat(created.status()).isEqualTo(PhotoRequest.OPEN);
        assertThat(created.reason()).isEqualTo("La photo est floue.");
        assertThat(created.notice()).isEqualTo(PhotoRequest.WAITING);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void reusesTheOpenRequest() {
        var reportId = UUID.randomUUID();
        var photoId = UUID.randomUUID();
        var existing = new PhotoRequest(
                UUID.randomUUID(),
                "NP-2026-000001",
                reportId,
                photoId,
                "Déjà demandée",
                PhotoRequest.REVIEW,
                UUID.randomUUID(),
                clock.instant());
        when(reportPhotoRepository.existsByIdAndReport_Id(photoId, reportId)).thenReturn(true);
        when(photoRequestRepository.findFirstByPhotoIdAndStatus(photoId, PhotoRequest.OPEN)).thenReturn(Optional.of(existing));
        var command = new RequestPhotoCommand(
                reportPhotoRepository, photoRequestRepository, readDocumentReadingsQuery, clock);

        var again = command.execute(reportId, photoId, "Autre motif", UUID.randomUUID());

        assertThat(again.referenceCode()).isEqualTo("NP-2026-000001");
        verify(photoRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesToLinkTheSameReport() {
        var reportId = UUID.randomUUID();
        var request = new PhotoRequest(
                UUID.randomUUID(),
                "NP-2026-000002",
                reportId,
                UUID.randomUUID(),
                "Illisible",
                PhotoRequest.REVIEW,
                UUID.randomUUID(),
                clock.instant());
        when(photoRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(reportRepository.findIdsByVisibleReference("origine")).thenReturn(List.of(reportId));
        var command = new LinkPhotoRequestCommand(photoRequestRepository, reportRepository, clock);

        assertThatThrownBy(() -> command.execute(reportId, request.getId(), "origine"))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("same-report");
        assertThat(request.getStatus()).isEqualTo(PhotoRequest.OPEN);
        assertThat(request.getLinkedReportId()).isNull();
    }

    @Test
    void linksAnotherReportWithoutChangingTheRequestReason() {
        var reportId = UUID.randomUUID();
        var otherId = UUID.randomUUID();
        var request = new PhotoRequest(
                UUID.randomUUID(),
                "NP-2026-000003",
                reportId,
                UUID.randomUUID(),
                "Illisible",
                PhotoRequest.REVIEW,
                UUID.randomUUID(),
                clock.instant());
        when(photoRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(reportRepository.findIdsByVisibleReference("autre-001")).thenReturn(List.of(otherId));
        when(reportRepository.findReferencesByIdIn(List.of(otherId))).thenReturn(List.of());
        when(photoRequestRepository.saveAndFlush(request)).thenReturn(request);
        var command = new LinkPhotoRequestCommand(photoRequestRepository, reportRepository, clock);

        var linked = command.execute(reportId, request.getId(), "autre-001");

        assertThat(request.getStatus()).isEqualTo(PhotoRequest.LINKED);
        assertThat(request.getLinkedReportId()).isEqualTo(otherId);
        assertThat(request.getReason()).isEqualTo("Illisible");
        assertThat(linked.resolvedAt()).isEqualTo(clock.instant());
        verify(reportRepository, never()).save(any());
    }
}
