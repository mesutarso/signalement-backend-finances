package cd.gouv.finances.signalements.report.command;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.dto.PhotoRequestResponse;
import cd.gouv.finances.signalements.report.entity.PhotoRequest;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component
public class RequestPhotoCommand {

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','ADMINISTRATOR')")
    public PhotoRequestResponse execute(UUID reportId, UUID photoId, String reason, UUID actorId) {
        throw new ReportRejected("photo-request-retired", 410);
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
