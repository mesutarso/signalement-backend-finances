package cd.gouv.finances.signalements.ocr.port;

import cd.gouv.finances.signalements.ocr.command.ReadingToStore;
import cd.gouv.finances.signalements.ocr.dto.ReadingResponse;
import java.util.Optional;

public interface ReadingStore {

    Optional<ReadingResponse> alreadyRead(String documentReference, String fingerprint, String model);

    ReadingResponse store(ReadingToStore reading);
}
