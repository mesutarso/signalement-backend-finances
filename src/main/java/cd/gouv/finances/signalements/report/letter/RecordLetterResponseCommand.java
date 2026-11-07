package cd.gouv.finances.signalements.report.letter;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.Letter;
import cd.gouv.finances.signalements.report.entity.LetterResponse;
import cd.gouv.finances.signalements.report.repository.LetterRepository;
import cd.gouv.finances.signalements.report.repository.LetterResponseRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RecordLetterResponseCommand {

    private static final ZoneId KINSHASA = ZoneId.of("Africa/Kinshasa");

    private final LetterRepository letterRepository;
    private final LetterResponseRepository letterResponseRepository;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public boolean execute(UUID letterId, String receivedOn, String service, String reference, String conclusion, byte[] content, UUID actorId) {
        var letter = letterRepository.findById(letterId).orElseThrow(() -> new ReportRejected("letter-absent", 404));
        if (!Letter.VALIDATED.equals(letter.getStatus())) {
            throw new ReportRejected("response-letter", 409);
        }
        var date = date(receivedOn);
        var keptService = required(service, 160);
        var keptReference = required(reference, 80);
        var keptConclusion = required(conclusion, 500);
        var document = LetterResponseDocument.accept(content);
        var existing = letterResponseRepository.findByLetterIdAndReferenceCode(letterId, keptReference);
        if (existing.isPresent()) {
            var response = existing.get();
            var same = response.getService().equals(keptService)
                    && response.getReceivedOn().equals(date)
                    && response.getConclusion().equals(keptConclusion)
                    && response.getFingerprint().equals(document.fingerprint());
            if (same) {
                return false;
            }
            throw new ReportRejected("response-duplicate", 409);
        }
        try {
            letterResponseRepository.saveAndFlush(new LetterResponse(
                    UUID.randomUUID(),
                    letterId,
                    date,
                    keptService,
                    keptReference,
                    keptConclusion,
                    content,
                    document.contentType(),
                    document.fingerprint(),
                    actorId,
                    clock.instant()));
        } catch (DataIntegrityViolationException exception) {
            throw new ReportRejected("response-duplicate", 409);
        }
        return true;
    }

    private LocalDate date(String receivedOn) {
        LocalDate date;
        try {
            date = LocalDate.parse(receivedOn);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new ReportRejected("response-date", 400);
        }
        if (date.isAfter(LocalDate.now(clock.withZone(KINSHASA)))) {
            throw new ReportRejected("response-future", 400);
        }
        return date;
    }

    private static String required(String value, int max) {
        var trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new ReportRejected("response-field", 400);
        }
        if (trimmed.length() > max) {
            throw new ReportRejected("response-length", 400);
        }
        return trimmed;
    }
}
