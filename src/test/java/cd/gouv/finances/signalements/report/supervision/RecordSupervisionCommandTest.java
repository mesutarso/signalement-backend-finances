package cd.gouv.finances.signalements.report.supervision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.Qualification;
import cd.gouv.finances.signalements.report.entity.SupervisionReview;
import cd.gouv.finances.signalements.report.repository.QualificationRepository;
import cd.gouv.finances.signalements.report.repository.SupervisionReviewRepository;
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

@ExtendWith(MockitoExtension.class)
class RecordSupervisionCommandTest {

    @Mock
    private QualificationRepository qualificationRepository;

    @Mock
    private SupervisionReviewRepository supervisionReviewRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T22:00:00Z"), ZoneOffset.UTC);

    @Test
    void keepsTheQualificationAndRecordsTheReview() {
        var reportId = UUID.randomUUID();
        var qualification = qualification(reportId);
        when(qualificationRepository.findByReportIdOrderByRecordedAtDesc(reportId)).thenReturn(List.of(qualification));
        when(supervisionReviewRepository.findFirstByQualificationIdOrderByRecordedAtDesc(qualification.getId()))
                .thenReturn(Optional.empty());
        var command = new RecordSupervisionCommand(qualificationRepository, supervisionReviewRepository, clock);

        var created = command.execute(reportId, "returned", "  Le motif est trop bref.  ", UUID.randomUUID());

        assertThat(created).isTrue();

        var saved = ArgumentCaptor.forClass(SupervisionReview.class);
        verify(supervisionReviewRepository).save(saved.capture());
        assertThat(saved.getValue().getQualificationId()).isEqualTo(qualification.getId());
        assertThat(saved.getValue().getResult()).isEqualTo("returned");
        assertThat(saved.getValue().getReason()).isEqualTo("Le motif est trop bref.");
        assertThat(qualification.getDecision()).isEqualTo("confirm");
    }

    @Test
    void aReportWithoutAQualificationCannotBeReviewed() {
        var reportId = UUID.randomUUID();
        when(qualificationRepository.findByReportIdOrderByRecordedAtDesc(reportId)).thenReturn(List.of());
        var command = new RecordSupervisionCommand(qualificationRepository, supervisionReviewRepository, clock);

        assertThatThrownBy(() -> command.execute(reportId, "upheld", "Rien à reprendre.", UUID.randomUUID()))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("supervision-absent");
        verify(supervisionReviewRepository, never()).save(any());
    }

    @Test
    void theSameResultAndReasonAreNotRecordedAgain() {
        var reportId = UUID.randomUUID();
        var qualification = qualification(reportId);
        var review = new SupervisionReview(
                UUID.randomUUID(),
                reportId,
                qualification.getId(),
                "upheld",
                "Le motif tient.",
                UUID.randomUUID(),
                clock.instant());
        when(qualificationRepository.findByReportIdOrderByRecordedAtDesc(reportId)).thenReturn(List.of(qualification));
        when(supervisionReviewRepository.findFirstByQualificationIdOrderByRecordedAtDesc(qualification.getId()))
                .thenReturn(Optional.of(review));
        var command = new RecordSupervisionCommand(qualificationRepository, supervisionReviewRepository, clock);

        var created = command.execute(reportId, "upheld", "Le motif tient.", UUID.randomUUID());

        assertThat(created).isFalse();
        verify(supervisionReviewRepository, never()).save(any());
    }

    private Qualification qualification(UUID reportId) {
        return new Qualification(
                UUID.randomUUID(),
                reportId,
                "confirm",
                "non-normalized",
                "photo",
                "La facture n'est pas normalisée.",
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
    }
}
