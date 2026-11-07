package cd.gouv.finances.signalements.ocr.command;

import cd.gouv.finances.signalements.ocr.DocumentRejected;
import cd.gouv.finances.signalements.ocr.EngineUnavailable;
import cd.gouv.finances.signalements.ocr.ExtractedArticle;
import cd.gouv.finances.signalements.ocr.ReadingQuality;
import cd.gouv.finances.signalements.ocr.ReadingStatus;
import cd.gouv.finances.signalements.ocr.dto.ReadingResponse;
import cd.gouv.finances.signalements.ocr.port.DocumentReader;
import cd.gouv.finances.signalements.ocr.port.ReadingStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RequestReadingCommand {

    static final int MAX_SIZE = 8_000_000;

    private static final Set<String> ACCEPTED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf");

    private final DocumentReader documentReader;
    private final ReadingStore readingStore;

    public ReadingResponse execute(String documentReference, byte[] content, String mediaType) {
        check(documentReference, content, mediaType);
        var fingerprint = fingerprint(content);
        var alreadyRead = readingStore.alreadyRead(documentReference, fingerprint);
        if (alreadyRead.isPresent()) {
            return alreadyRead.get();
        }
        try {
            var response = documentReader.read(content, mediaType);
            var articles = response.articles() == null
                    ? List.<ExtractedArticle>of()
                    : response.articles().stream().filter(ExtractedArticle::present).toList();
            return readingStore.store(new ReadingToStore(
                    documentReference,
                    fingerprint,
                    ReadingStatus.READ,
                    ReadingQuality.from(response.fields(), !articles.isEmpty()),
                    response.model(),
                    null,
                    response.confidence(),
                    response.fields(),
                    articles));
        } catch (EngineUnavailable unavailable) {
            return readingStore.store(new ReadingToStore(
                    documentReference,
                    fingerprint,
                    ReadingStatus.FAILED,
                    null,
                    null,
                    unavailable.code(),
                    null,
                    List.of(),
                    List.of()));
        }
    }

    private static void check(String documentReference, byte[] content, String mediaType) {
        if (documentReference == null || documentReference.isBlank()) {
            throw new DocumentRejected("missing-reference");
        }
        if (content == null || content.length == 0) {
            throw new DocumentRejected("empty");
        }
        if (content.length > MAX_SIZE) {
            throw new DocumentRejected("too-large");
        }
        if (mediaType == null || !ACCEPTED_TYPES.contains(mediaType.toLowerCase(Locale.ROOT))) {
            throw new DocumentRejected("rejected-type");
        }
    }

    private static String fingerprint(byte[] content) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
