package cd.gouv.finances.signalements.report.command;

import cd.gouv.finances.signalements.ocr.query.ReadDocumentReadingsQuery;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.dto.PhotoRequestResponse;
import cd.gouv.finances.signalements.report.entity.PhotoRequest;
import cd.gouv.finances.signalements.report.repository.PhotoRequestRepository;
import cd.gouv.finances.signalements.report.repository.ReportPhotoRepository;
import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RequestPhotoCommand {

    private static final ZoneId KINSHASA = ZoneId.of("Africa/Kinshasa");

    private final ReportPhotoRepository reportPhotoRepository;
    private final PhotoRequestRepository photoRequestRepository;
    private final ReadDocumentReadingsQuery readDocumentReadingsQuery;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public PhotoRequestResponse execute(UUID reportId, UUID photoId, String reason, UUID actorId) {
        if (!reportPhotoRepository.existsByIdAndReport_Id(photoId, reportId)) {
            throw new ReportRejected("photo-absent", 404);
        }
        var open = photoRequestRepository.findFirstByPhotoIdAndStatus(photoId, PhotoRequest.OPEN);
        if (open.isPresent()) {
            return response(open.get(), null);
        }
        var created = new PhotoRequest(
                UUID.randomUUID(),
                reference(),
                reportId,
                photoId,
                reason.trim(),
                basis(photoId),
                actorId,
                clock.instant());
        try {
            return response(photoRequestRepository.saveAndFlush(created), null);
        } catch (DataIntegrityViolationException exception) {
            throw new ReportRejected("request-open", 409);
        }
    }

    private String reference() {
        var year = clock.instant().atZone(KINSHASA).getYear();
        return "NP-" + year + "-" + String.format("%06d", photoRequestRepository.nextNumber());
    }

    private String basis(UUID photoId) {
        var reading = readDocumentReadingsQuery.latest(java.util.List.of(photoId.toString())).get(photoId.toString());
        if (reading != null && "READ".equals(reading.status()) && "UNREADABLE".equals(reading.quality())) {
            return PhotoRequest.READING;
        }
        return PhotoRequest.REVIEW;
    }

    static PhotoRequestResponse response(PhotoRequest request, String linkedIdentifier) {
        return new PhotoRequestResponse(
                request.getId(),
                request.getPhotoId(),
                request.getReferenceCode(),
                request.getReason(),
                request.getBasis(),
                request.getStatus(),
                null,
                request.getCreatedAt(),
                request.getResolvedAt(),
                linkedIdentifier,
                request.getNotice());
    }
}
