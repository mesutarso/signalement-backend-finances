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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
        public Optional<ReadingResponse> alreadyRead(String documentReference, String fingerprint, String model) {
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
                    reading.confidence(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    reading.errorCode());
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

    @Test
    void anExplicitRefreshCreatesAnotherAttempt() {
        var first = command.execute("photo-1", new byte[] {1}, "image/png");
        var next = command.execute("photo-1", new byte[] {1}, "image/png", true);

        assertThat(engineCalls).isEqualTo(2);
        assertThat(next.id()).isNotEqualTo(first.id());
    }

    @Test
    void failedAttemptsCanBeRetried() {
        command.execute("photo-1", new byte[] {9}, "image/png");
        command.execute("photo-1", new byte[] {9}, "image/png");
        assertThat(engineCalls).isEqualTo(2);
    }

    @Test
    void documentsOutsideTheFlaskLimitsNeverCallTheEngine() {
        assertThatThrownBy(() -> command.execute("photo-1", new byte[5 * 1024 * 1024 + 1], "image/png"))
                .isInstanceOf(DocumentRejected.class).hasMessage("too-large");
        assertThatThrownBy(() -> command.execute("photo-1", new byte[] {1}, "application/pdf"))
                .isInstanceOf(DocumentRejected.class).hasMessage("rejected-type");
        assertThat(engineCalls).isZero();
    }

    @Test
    void simultaneousRequestsForOnePhotoDoNotCallTheEngineTwice() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        DocumentReader slowReader = (content, type) -> {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
            return reader.read(content, type);
        };
        var guarded = new RequestReadingCommand(slowReader, store);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> guarded.execute("photo-1", new byte[] {1}, "image/png", true));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> guarded.execute("photo-1", new byte[] {1}, "image/png", true))
                        .isInstanceOf(DocumentRejected.class).hasMessage("reading-in-progress");
            } finally {
                release.countDown();
            }
            assertThat(first.get(5, TimeUnit.SECONDS).status()).isEqualTo("READ");
            guarded.execute("photo-1", new byte[] {1}, "image/png", true);
            assertThat(engineCalls).isEqualTo(2);
        }
    }
}
