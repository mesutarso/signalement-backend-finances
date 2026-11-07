package cd.gouv.finances.signalements.report.letter;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.dto.PhotoFile;
import cd.gouv.finances.signalements.report.repository.LetterResponseRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReadLetterResponseFileQuery {

    private final LetterResponseRepository letterResponseRepository;

    @PreAuthorize("hasAnyRole('SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public PhotoFile execute(UUID letterId, UUID responseId) {
        var response = letterResponseRepository.findByIdAndLetterId(responseId, letterId)
                .orElseThrow(() -> new ReportRejected("response-absent", 404));
        return new PhotoFile(response.getContentType(), response.getContent());
    }
}
