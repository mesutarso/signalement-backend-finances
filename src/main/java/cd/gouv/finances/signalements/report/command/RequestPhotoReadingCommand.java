package cd.gouv.finances.signalements.report.command;

import cd.gouv.finances.signalements.ocr.command.RequestReadingCommand;
import cd.gouv.finances.signalements.report.dto.PhotoReadingResponse;
import cd.gouv.finances.signalements.report.mapper.PhotoReadingMapper;
import cd.gouv.finances.signalements.report.query.ReadReportPhotoQuery;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RequestPhotoReadingCommand {

    private final ReadReportPhotoQuery readReportPhotoQuery;
    private final RequestReadingCommand requestReadingCommand;
    private final cd.gouv.finances.signalements.report.validation.AnalysisJobs analysisJobs;

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','ADMINISTRATOR')")
    public PhotoReadingResponse execute(UUID reportId, UUID photoId, boolean refresh) {
        var photo = readReportPhotoQuery.execute(reportId, photoId);
        var reading = requestReadingCommand.execute(photoId.toString(), photo.content(), mediaType(photo.contentType()), refresh);
        analysisJobs.enqueue(reportId, false);
        return PhotoReadingMapper.from(reading);
    }

    private static String mediaType(String contentType) {
        if (contentType == null) {
            return "";
        }
        var separator = contentType.indexOf(';');
        var type = separator < 0 ? contentType : contentType.substring(0, separator);
        return type.trim().toLowerCase(Locale.ROOT);
    }
}
