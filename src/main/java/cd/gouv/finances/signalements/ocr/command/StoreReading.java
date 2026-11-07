package cd.gouv.finances.signalements.ocr.command;

import cd.gouv.finances.signalements.ocr.DocumentContract;
import cd.gouv.finances.signalements.ocr.ReadingStatus;
import cd.gouv.finances.signalements.ocr.dto.ReadingResponse;
import cd.gouv.finances.signalements.ocr.entity.DocumentReading;
import cd.gouv.finances.signalements.ocr.entity.ReadingArticle;
import cd.gouv.finances.signalements.ocr.entity.StoredField;
import cd.gouv.finances.signalements.ocr.mapper.ReadingMapper;
import cd.gouv.finances.signalements.ocr.port.ReadingStore;
import cd.gouv.finances.signalements.ocr.repository.ReadingRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class StoreReading implements ReadingStore {

    private final ReadingRepository readingRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<ReadingResponse> alreadyRead(String documentReference, String fingerprint) {
        return readingRepository
                .findFirstByDocumentReferenceAndFingerprintAndStatusOrderByCreatedAtDesc(
                        documentReference, fingerprint, ReadingStatus.READ)
                .map(ReadingMapper::toResponse);
    }

    @Override
    @Transactional
    public ReadingResponse store(ReadingToStore reading) {
        var entity = new DocumentReading(
                UUID.randomUUID(),
                reading.documentReference(),
                reading.fingerprint(),
                reading.status(),
                reading.quality(),
                reading.model(),
                DocumentContract.VERSION,
                reading.errorCode(),
                Instant.now());
        entity.read(reading.confidence());
        for (var field : reading.fields()) {
            entity.add(new StoredField(UUID.randomUUID(), field.name(), field.value(), field.present()));
        }
        var position = 0;
        for (var article : reading.articles()) {
            entity.add(new ReadingArticle(UUID.randomUUID(), position++, article.designation(), article.amount()));
        }
        return ReadingMapper.toResponse(readingRepository.save(entity));
    }
}
