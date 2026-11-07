package cd.gouv.finances.signalements.report.letter;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.Letter;
import cd.gouv.finances.signalements.report.repository.LetterRepository;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DecideLetterCommand {

    private static final Set<String> RESULTS = Set.of(Letter.VALIDATED, Letter.RETURNED);

    private final LetterRepository letterRepository;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public boolean execute(UUID letterId, String result, String recipient, String reason, UUID actorId) {
        if (!RESULTS.contains(result)) {
            throw new ReportRejected("letter-result", 400);
        }
        var letter = letterRepository.findById(letterId).orElseThrow(() -> new ReportRejected("letter-absent", 404));
        if (!Letter.DRAFT.equals(letter.getStatus())) {
            throw new ReportRejected("letter-decided", 409);
        }
        var trimmedReason = reason == null ? "" : reason.trim();
        if (Letter.RETURNED.equals(result) && trimmedReason.isEmpty()) {
            throw new ReportRejected("letter-reason", 400);
        }
        var keptRecipient = Letter.VALIDATED.equals(result) ? blankToNull(recipient) : null;
        var keptReason = Letter.RETURNED.equals(result) ? trimmedReason : null;
        letter.decide(result, keptRecipient, keptReason, actorId, clock.instant());
        letterRepository.save(letter);
        return true;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
