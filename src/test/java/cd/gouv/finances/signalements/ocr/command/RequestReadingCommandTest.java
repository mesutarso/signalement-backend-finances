package cd.gouv.finances.signalements.ocr.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cd.gouv.finances.signalements.ocr.DocumentRejected;
import cd.gouv.finances.signalements.ocr.EngineUnavailable;
import cd.gouv.finances.signalements.ocr.EngineResponse;
import cd.gouv.finances.signalements.ocr.ExtractedField;
import cd.gouv.finances.signalements.ocr.ReadingQuality;
import cd.gouv.finances.signalements.ocr.ReadingStatus;
import cd.gouv.finances.signalements.ocr.dto.ReadingResponse;
import cd.gouv.finances.signalements.ocr.port.DocumentReader;
import cd.gouv.finances.signalements.ocr.port.ReadingStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestReadingCommandTest {

    private final List<String> readFingerprints = new ArrayList<>();
    private int engineCalls;

    private final DocumentReader reader = (content, mediaType) -> {
        engineCalls++;
        if (content[0] == 9) {
            throw new EngineUnavailable("unavailable");
        }
        return new EngineResponse("mistral-ocr-latest", List.of(
                new ExtractedField("documentNumber", "F-1"),
                new ExtractedField("sellerName", "Kiosk"),
                new ExtractedField("totalAmount", "15000")));
    };

    private final ReadingStore store = new ReadingStore() {
        private ReadingResponse latest;

        @Override
        public Optional<ReadingResponse> alreadyRead(String documentReference, String fingerprint) {
            if (readFingerprints.contains(documentReference + fingerprint)) {
                return Optional.of(latest);
            }
            return Optional.empty();
        }

        @Override
        public ReadingResponse store(ReadingToStore reading) {
            if (reading.status() == ReadingStatus.READ) {
                readFingerprints.add(reading.documentReference() + reading.fingerprint());
            }
            latest = new ReadingResponse(
                    UUID.randomUUID(),
                    reading.documentReference(),
                    reading.status().name(),
                    reading.quality() == null ? null : reading.quality().name(),
                    "document-v1",
                    reading.model(),
                    reading.errorCode(),
                    List.of());
            return latest;
        }
    };

    private final RequestReadingCommand command = new RequestReadingCommand(reader, store);

    @Test
    void theSameDocumentIsNotSentAgain() {
        command.execute("facno-1", new byte[] {1, 2, 3}, "image/jpeg");
        var second = command.execute("facno-1", new byte[] {1, 2, 3}, "image/jpeg");

        assertThat(engineCalls).isEqualTo(1);
        assertThat(second.status()).isEqualTo("READ");
        assertThat(second.quality()).isEqualTo(ReadingQuality.READABLE.name());
    }

    @Test
    void anOutageIsStoredForRetry() {
        var reading = command.execute("facno-2", new byte[] {9, 1}, "image/png");

        assertThat(reading.status()).isEqualTo("FAILED");
        assertThat(reading.errorCode()).isEqualTo("unavailable");
        assertThat(reading.quality()).isNull();
    }

    @Test
    void anUnknownTypeDoesNotCallTheEngine() {
        assertThatThrownBy(() -> command.execute("facno-3", new byte[] {1}, "text/plain"))
                .isInstanceOf(DocumentRejected.class);
        assertThat(engineCalls).isZero();
    }
}
