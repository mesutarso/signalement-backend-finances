package cd.gouv.finances.signalements.report.mapper;

import cd.gouv.finances.signalements.report.assignment.AssigneeDirectory;
import cd.gouv.finances.signalements.report.dto.AssignmentResponse;
import cd.gouv.finances.signalements.report.dto.ImportResponse;
import cd.gouv.finances.signalements.report.dto.PhotoReadingResponse;
import cd.gouv.finances.signalements.report.dto.DuplicateAlertResponse;
import cd.gouv.finances.signalements.report.dto.PhotoRequestResponse;
import cd.gouv.finances.signalements.report.dto.QualificationResponse;
import cd.gouv.finances.signalements.report.dto.SupervisionReviewResponse;
import cd.gouv.finances.signalements.report.entity.Qualification;
import cd.gouv.finances.signalements.report.entity.SupervisionReview;
import cd.gouv.finances.signalements.report.dto.PhotoResponse;
import cd.gouv.finances.signalements.report.dto.ReportPageResponse;
import cd.gouv.finances.signalements.report.dto.ReportResponse;
import cd.gouv.finances.signalements.report.dto.ReportSummaryResponse;
import cd.gouv.finances.signalements.report.entity.Report;
import cd.gouv.finances.signalements.report.entity.ReportAssignment;
import cd.gouv.finances.signalements.report.entity.ReportImport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;

public final class ReportMapper {

    private ReportMapper() {
    }

    public static ReportSummaryResponse toSummary(Report report, boolean includeReporter, Map<UUID, String> assigneeNames) {
        return new ReportSummaryResponse(
                report.getId(),
                report.getExternalId(),
                report.getReportedAt(),
                report.getTitle(),
                report.getState(),
                report.getPriority(),
                AssigneeDirectory.name(assigneeNames, report.getAssigneeId()),
                report.getVersion(),
                includeReporter ? report.getReporterName() : null,
                includeReporter ? report.getReporterEmail() : null,
                includeReporter ? report.getReporterPhone() : null);
    }

    public static ReportPageResponse toPage(Page<Report> page, boolean includeReporter, Map<UUID, String> assigneeNames) {
        return new ReportPageResponse(
                page.getContent().stream().map(report -> toSummary(report, includeReporter, assigneeNames)).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }

    public static ReportResponse toResponse(
            Report report,
            boolean includeReporter,
            Map<UUID, String> names,
            List<ReportAssignment> history,
            Map<UUID, PhotoReadingResponse> readings,
            List<PhotoRequestResponse> photoRequests,
            List<DuplicateAlertResponse> duplicates,
            List<Qualification> qualifications,
            List<SupervisionReview> reviews) {
        return new ReportResponse(
                report.getId(),
                report.getExternalId(),
                report.getReportedAt(),
                report.getTitle(),
                report.getDescription(),
                report.getState(),
                report.getPriority(),
                AssigneeDirectory.name(names, report.getAssigneeId()),
                report.getAssigneeId(),
                report.getVersion(),
                includeReporter ? report.getReporterName() : null,
                includeReporter ? report.getReporterEmail() : null,
                includeReporter ? report.getReporterPhone() : null,
                includeReporter ? report.getReporterUserId() : null,
                report.getPhotos().stream()
                        .map(photo -> new PhotoResponse(
                                photo.getId(),
                                photo.getContentType(),
                                readings.get(photo.getId())))
                        .toList(),
                history.stream().map(entry -> new AssignmentResponse(
                        entry.getRecordedAt(),
                        AssigneeDirectory.name(names, entry.getActorId()),
                        AssigneeDirectory.name(names, entry.getAssigneeId()),
                        AssigneeDirectory.name(names, entry.getPreviousAssigneeId()),
                        entry.getPriority(),
                        entry.getState())).toList(),
                photoRequests,
                duplicates,
                qualifications.stream().map(entry -> new QualificationResponse(
                        entry.getId(),
                        entry.getDecision(),
                        entry.getFinding(),
                        entry.getProof(),
                        entry.getJustification(),
                        entry.getSellerName(),
                        entry.getSellerTaxId(),
                        entry.getIssuedOn(),
                        entry.getAmount(),
                        entry.getDocumentNumber(),
                        entry.getCode(),
                        entry.getMissingElements(),
                        entry.getDgiReference(),
                        AssigneeDirectory.name(names, entry.getActorId()),
                        entry.getRecordedAt())).toList(),
                reviews.stream().map(entry -> new SupervisionReviewResponse(
                        entry.getId(),
                        entry.getQualificationId(),
                        entry.getResult(),
                        entry.getReason(),
                        AssigneeDirectory.name(names, entry.getActorId()),
                        entry.getRecordedAt())).toList());
    }

    public static ImportResponse toImport(ReportImport run) {
        return new ImportResponse(
                run.getId(),
                run.getStatus(),
                run.getCreatedReports(),
                run.getSkippedReports(),
                run.getCreatedPhotos(),
                run.getErrorCode(), run.getStartedAt(), run.getFinishedAt(),
                run.getPeriodStart(), run.getPeriodEnd(), run.getTriggerType());
    }
}
