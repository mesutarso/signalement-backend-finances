package cd.gouv.finances.signalements.report.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cd.gouv.finances.signalements.ocr.query.ReadDocumentReadingsQuery;
import cd.gouv.finances.signalements.report.assignment.AssigneeDirectory;
import cd.gouv.finances.signalements.report.entity.PhotoRequest;
import cd.gouv.finances.signalements.report.entity.Report;
import cd.gouv.finances.signalements.report.duplicate.DuplicateLookup;
import cd.gouv.finances.signalements.report.repository.DuplicateAlertRepository;
import cd.gouv.finances.signalements.report.repository.QualificationRepository;
import cd.gouv.finances.signalements.report.repository.SupervisionReviewRepository;
import cd.gouv.finances.signalements.report.repository.PhotoRequestRepository;
import cd.gouv.finances.signalements.report.repository.ReportAssignmentRepository;
import cd.gouv.finances.signalements.report.repository.ReportRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadReportQueryTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ReportAssignmentRepository reportAssignmentRepository;

    @Mock
    private AssigneeDirectory assigneeDirectory;

    @Mock
    private ReadDocumentReadingsQuery readDocumentReadingsQuery;

    @Mock
    private PhotoRequestRepository photoRequestRepository;

    @Mock
    private DuplicateAlertRepository duplicateAlertRepository;

    @Mock
    private DuplicateLookup duplicateLookup;

    @Mock
    private QualificationRepository qualificationRepository;

    @Mock
    private SupervisionReviewRepository supervisionReviewRepository;

    @Test
    void hidesTheReporterFromAnAuditor() {
        var id = UUID.randomUUID();
        when(reportRepository.findById(id)).thenReturn(Optional.of(new Report(
                id,
                "signalement-001",
                "facno",
                "signalement-001",
                LocalDate.parse("2026-10-19"),
                LocalDateTime.parse("2026-10-19T10:30:00"),
                "Facture non délivrée",
                "Le commerçant n'a pas remis de facture.",
                "Claire Mbuyi",
                "claire.mbuyi@exemple.cd",
                "+243810000000",
                "utilisateur-001",
                Instant.parse("2026-11-06T00:00:00Z"))));
        when(reportAssignmentRepository.findByReportIdOrderByRecordedAtDesc(id)).thenReturn(List.of());
        when(assigneeDirectory.names(java.util.Set.of())).thenReturn(Map.of());
        when(readDocumentReadingsQuery.latest(any())).thenReturn(Map.of());
        when(photoRequestRepository.findByReportIdOrderByCreatedAtDesc(id)).thenReturn(List.of());
        when(duplicateAlertRepository.findByReportIdOrOtherReportIdOrderByCreatedAtDesc(id, id)).thenReturn(List.of());
        when(duplicateLookup.timings(any())).thenReturn(List.of());
        when(duplicateLookup.completeReports(any())).thenReturn(List.of());
        when(qualificationRepository.findByReportIdOrderByRecordedAtDesc(id)).thenReturn(List.of());
        when(supervisionReviewRepository.findByReportIdOrderByRecordedAtDesc(id)).thenReturn(List.of());
        var query = new ReadReportQuery(
                reportRepository,
                reportAssignmentRepository,
                assigneeDirectory,
                readDocumentReadingsQuery,
                photoRequestRepository,
                duplicateAlertRepository,
                duplicateLookup,
                qualificationRepository,
                supervisionReviewRepository);

        var hidden = query.execute(id, false);
        var visible = query.execute(id, true);

        assertThat(hidden.reporterName()).isNull();
        assertThat(hidden.reporterEmail()).isNull();
        assertThat(hidden.reporterPhone()).isNull();
        assertThat(hidden.reporterUserId()).isNull();
        assertThat(visible.reporterName()).isEqualTo("Claire Mbuyi");
        assertThat(visible.reporterEmail()).isEqualTo("claire.mbuyi@exemple.cd");
        assertThat(visible.identifier()).isEqualTo("signalement-001");
    }

    @Test
    void showsAnOpenPhotoRequestWhenNothingIsLinked() {
        var id = UUID.randomUUID();
        var actorId = UUID.randomUUID();
        when(reportRepository.findById(id)).thenReturn(Optional.of(new Report(
                id,
                "signalement-002",
                "facno",
                "signalement-002",
                LocalDate.parse("2026-10-19"),
                LocalDateTime.parse("2026-10-19T10:30:00"),
                "Facture non délivrée",
                "Le commerçant n'a pas remis de facture.",
                null,
                null,
                null,
                null,
                Instant.parse("2026-11-06T00:00:00Z"))));
        when(reportAssignmentRepository.findByReportIdOrderByRecordedAtDesc(id)).thenReturn(List.of());
        when(readDocumentReadingsQuery.latest(any())).thenReturn(Map.of());
        when(photoRequestRepository.findByReportIdOrderByCreatedAtDesc(id)).thenReturn(List.of(new PhotoRequest(
                UUID.randomUUID(),
                "NP-2026-000001",
                id,
                UUID.randomUUID(),
                "La pièce est illisible.",
                PhotoRequest.REVIEW,
                actorId,
                Instant.parse("2026-11-06T20:00:00Z"))));
        when(assigneeDirectory.names(any())).thenReturn(Map.of(actorId, "agent1"));
        when(duplicateAlertRepository.findByReportIdOrOtherReportIdOrderByCreatedAtDesc(id, id)).thenReturn(List.of());
        when(duplicateLookup.timings(any())).thenReturn(List.of());
        when(duplicateLookup.completeReports(any())).thenReturn(List.of());
        when(qualificationRepository.findByReportIdOrderByRecordedAtDesc(id)).thenReturn(List.of());
        when(supervisionReviewRepository.findByReportIdOrderByRecordedAtDesc(id)).thenReturn(List.of());
        var query = new ReadReportQuery(
                reportRepository,
                reportAssignmentRepository,
                assigneeDirectory,
                readDocumentReadingsQuery,
                photoRequestRepository,
                duplicateAlertRepository,
                duplicateLookup,
                qualificationRepository,
                supervisionReviewRepository);

        var detail = query.execute(id, false);

        assertThat(detail.photoRequests()).singleElement().satisfies(request -> {
            assertThat(request.referenceCode()).isEqualTo("NP-2026-000001");
            assertThat(request.linkedIdentifier()).isNull();
            assertThat(request.actorName()).isEqualTo("agent1");
        });
    }
}
