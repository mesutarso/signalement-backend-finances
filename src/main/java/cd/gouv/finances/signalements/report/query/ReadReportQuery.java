package cd.gouv.finances.signalements.report.query;

import cd.gouv.finances.signalements.ocr.query.ReadDocumentReadingsQuery;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.assignment.AssigneeDirectory;
import cd.gouv.finances.signalements.report.duplicate.DuplicateLookup;
import cd.gouv.finances.signalements.report.duplicate.DuplicatePriority;
import cd.gouv.finances.signalements.report.dto.DuplicateAlertResponse;
import cd.gouv.finances.signalements.report.dto.PhotoRequestResponse;
import cd.gouv.finances.signalements.report.dto.ReportResponse;
import cd.gouv.finances.signalements.report.entity.DuplicateAlert;
import cd.gouv.finances.signalements.report.entity.PhotoRequest;
import cd.gouv.finances.signalements.report.entity.ReportAssignment;
import cd.gouv.finances.signalements.report.mapper.PhotoReadingMapper;
import cd.gouv.finances.signalements.report.mapper.ReportMapper;
import cd.gouv.finances.signalements.report.repository.DuplicateAlertRepository;
import cd.gouv.finances.signalements.report.repository.QualificationRepository;
import cd.gouv.finances.signalements.report.repository.SupervisionReviewRepository;
import cd.gouv.finances.signalements.report.repository.PhotoRequestRepository;
import cd.gouv.finances.signalements.report.repository.ReportAssignmentRepository;
import cd.gouv.finances.signalements.report.repository.ReportRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReadReportQuery {

    private final ReportRepository reportRepository;
    private final ReportAssignmentRepository reportAssignmentRepository;
    private final AssigneeDirectory assigneeDirectory;
    private final ReadDocumentReadingsQuery readDocumentReadingsQuery;
    private final PhotoRequestRepository photoRequestRepository;
    private final DuplicateAlertRepository duplicateAlertRepository;
    private final DuplicateLookup duplicateLookup;
    private final QualificationRepository qualificationRepository;
    private final SupervisionReviewRepository supervisionReviewRepository;

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public ReportResponse execute(UUID id, boolean includeReporter) {
        var report = reportRepository.findById(id).orElseThrow(() -> new ReportRejected("report-absent", 404));
        var history = reportAssignmentRepository.findByReportIdOrderByRecordedAtDesc(id);
        var ids = new HashSet<UUID>();
        if (report.getAssigneeId() != null) {
            ids.add(report.getAssigneeId());
        }
        for (var entry : history) {
            ids.add(entry.getActorId());
            if (entry.getAssigneeId() != null) {
                ids.add(entry.getAssigneeId());
            }
            if (entry.getPreviousAssigneeId() != null) {
                ids.add(entry.getPreviousAssigneeId());
            }
        }
        var requests = photoRequestRepository.findByReportIdOrderByCreatedAtDesc(id);
        for (var request : requests) {
            ids.add(request.getActorId());
        }
        var alerts = duplicateAlertRepository.findByReportIdOrOtherReportIdOrderByCreatedAtDesc(id, id);
        for (var alert : alerts) {
            if (alert.getActorId() != null) {
                ids.add(alert.getActorId());
            }
        }
        var qualifications = qualificationRepository.findByReportIdOrderByRecordedAtDesc(id);
        for (var qualification : qualifications) {
            ids.add(qualification.getActorId());
        }
        var reviews = supervisionReviewRepository.findByReportIdOrderByRecordedAtDesc(id);
        for (var review : reviews) {
            ids.add(review.getActorId());
        }
        var linkedIds = requests.stream()
                .map(PhotoRequest::getLinkedReportId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        var linked = linkedIds.isEmpty()
                ? Map.<UUID, String>of()
                : reportRepository.findReferencesByIdIn(linkedIds).stream()
                        .collect(Collectors.toMap(
                                reference -> reference.getId(),
                                reference -> reference.getExternalId()));
        var readings = readDocumentReadingsQuery.latest(report.getPhotos().stream()
                        .map(photo -> photo.getId().toString())
                        .toList())
                .entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> UUID.fromString(entry.getKey()),
                        entry -> PhotoReadingMapper.from(entry.getValue())));
        var names = assigneeDirectory.names(ids);
        var photoRequests = requests.stream()
                .map(request -> new PhotoRequestResponse(
                        request.getId(),
                        request.getPhotoId(),
                        request.getReferenceCode(),
                        request.getReason(),
                        request.getBasis(),
                        request.getStatus(),
                        AssigneeDirectory.name(names, request.getActorId()),
                        request.getCreatedAt(),
                        request.getResolvedAt(),
                        request.getLinkedReportId() == null ? null : linked.get(request.getLinkedReportId()),
                        request.getNotice()))
                .toList();
        return ReportMapper.toResponse(
                report, includeReporter, names, history, readings, photoRequests, duplicates(id, alerts, names), qualifications, reviews);
    }

    private List<DuplicateAlertResponse> duplicates(UUID id, List<DuplicateAlert> alerts, Map<UUID, String> names) {
        var timingIds = new ArrayList<UUID>();
        timingIds.add(id);
        for (var alert : alerts) {
            timingIds.add(alert.getReportId());
            timingIds.add(alert.getOtherReportId());
            if (alert.getPriorityReportId() != null) {
                timingIds.add(alert.getPriorityReportId());
            }
        }
        var timings = duplicateLookup.timings(timingIds).stream()
                .collect(Collectors.toMap(DuplicateLookup.Timing::id, timing -> timing, (left, right) -> left));
        var complete = new HashSet<>(duplicateLookup.completeReports(timingIds));
        return alerts.stream().map(alert -> {
            var otherId = alert.getReportId().equals(id) ? alert.getOtherReportId() : alert.getReportId();
            var other = timings.get(otherId);
            var current = timings.get(id);
            var priority = alert.getPriorityReportId() == null ? null : timings.get(alert.getPriorityReportId());
            String basis = null;
            if (DuplicateAlert.CONFIRMED.equals(alert.getStatus()) && current != null && other != null && priority != null) {
                var currentSide = side(current, complete);
                var otherSide = side(other, complete);
                var winner = priority.id().equals(current.id()) ? currentSide : otherSide;
                var loser = winner == currentSide ? otherSide : currentSide;
                basis = DuplicatePriority.basis(winner, loser);
            }
            return new DuplicateAlertResponse(
                    alert.getId(),
                    otherId,
                    other == null ? null : other.identifier(),
                    other == null ? null : other.reportedAt(),
                    alert.getCriterion(),
                    alert.getStrength(),
                    alert.getExplanation(),
                    alert.getStatus(),
                    alert.getReason(),
                    AssigneeDirectory.name(names, alert.getActorId()),
                    alert.getDecidedAt(),
                    alert.getPriorityReportId(),
                    priority == null ? null : priority.identifier(),
                    priority == null ? null : priority.reportedAt(),
                    basis,
                    id.equals(alert.getPriorityReportId()));
        }).toList();
    }

    private static DuplicatePriority.Side side(DuplicateLookup.Timing timing, java.util.Set<UUID> complete) {
        return new DuplicatePriority.Side(
                timing.id(),
                timing.identifier(),
                timing.reportedAt(),
                timing.referenceCode(),
                complete.contains(timing.id()));
    }
}
