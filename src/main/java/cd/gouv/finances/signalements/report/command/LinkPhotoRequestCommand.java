package cd.gouv.finances.signalements.report.command;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.dto.PhotoRequestResponse;
import cd.gouv.finances.signalements.report.entity.PhotoRequest;
import cd.gouv.finances.signalements.report.repository.PhotoRequestRepository;
import cd.gouv.finances.signalements.report.repository.ReportRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LinkPhotoRequestCommand {

    private final PhotoRequestRepository photoRequestRepository;
    private final ReportRepository reportRepository;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public PhotoRequestResponse execute(UUID reportId, UUID requestId, String referenceCode) {
        var request = photoRequestRepository.findById(requestId)
                .filter(found -> found.getReportId().equals(reportId))
                .orElseThrow(() -> new ReportRejected("request-absent", 404));
        var matches = reportRepository.findIdsByVisibleReference(referenceCode.trim());
        if (matches.isEmpty()) {
            throw new ReportRejected("report-absent", 404);
        }
        if (matches.size() > 1) {
            throw new ReportRejected("ambiguous-report", 409);
        }
        var linkedId = matches.getFirst();
        if (linkedId.equals(reportId)) {
            throw new ReportRejected("same-report", 400);
        }
        if (PhotoRequest.LINKED.equals(request.getStatus())) {
            if (linkedId.equals(request.getLinkedReportId())) {
                return RequestPhotoCommand.response(request, identifier(linkedId, referenceCode));
            }
            throw new ReportRejected("already-linked", 409);
        }
        request.link(linkedId, clock.instant());
        var saved = photoRequestRepository.saveAndFlush(request);
        return RequestPhotoCommand.response(saved, identifier(linkedId, referenceCode));
    }

    private String identifier(UUID linkedId, String typed) {
        return reportRepository.findReferencesByIdIn(List.of(linkedId)).stream()
                .map(reference -> reference.getExternalId())
                .findFirst()
                .orElse(typed.trim());
    }
}
