package cd.gouv.finances.signalements.ocr.query;

import cd.gouv.finances.signalements.ocr.ReadingStatus;
import cd.gouv.finances.signalements.ocr.dto.ReadingResponse;
import cd.gouv.finances.signalements.ocr.mapper.ReadingMapper;
import cd.gouv.finances.signalements.ocr.repository.ReadingRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReadDocumentReadingsQuery {

    private final ReadingRepository readingRepository;

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public Map<String, ReadingResponse> latest(Collection<String> references) {
        if (references == null || references.isEmpty()) {
            return Map.of();
        }
        var chosen = new HashMap<String, ReadingResponse>();
        for (var reading : readingRepository.findByDocumentReferenceInOrderByCreatedAtDesc(references)) {
            var current = chosen.get(reading.getDocumentReference());
            if (current == null || (reading.getStatus() == ReadingStatus.READ && !"READ".equals(current.status()))) {
                chosen.put(reading.getDocumentReference(), ReadingMapper.toResponse(reading));
            }
        }
        return Map.copyOf(chosen);
    }
}
