package cd.gouv.finances.signalements.report.letter;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.Letter;
import cd.gouv.finances.signalements.report.entity.LetterLine;
import cd.gouv.finances.signalements.report.repository.LetterLineRepository;
import cd.gouv.finances.signalements.report.repository.LetterRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PrepareLetterCommand {

    private static final ZoneId KINSHASA = ZoneId.of("Africa/Kinshasa");

    private final LetterRepository letterRepository;
    private final LetterLineRepository letterLineRepository;
    private final LetterSourceLoader letterSourceLoader;
    private final Clock clock;

    public record Prepared(UUID id, boolean created) {
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public Prepared execute(UUID actorId) {
        var start = LocalDate.now(clock.withZone(KINSHASA)).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var existing = letterRepository.findFirstByPeriodStartAndStatus(start, Letter.DRAFT);
        if (existing.isPresent()) {
            return new Prepared(existing.get().getId(), false);
        }
        if (letterRepository.findFirstByPeriodStartAndStatus(start, Letter.VALIDATED).isPresent()) {
            throw new ReportRejected("letter-closed", 409);
        }
        var findings = letterSourceLoader.findings();
        if (findings.isEmpty()) {
            throw new ReportRejected("letter-empty", 404);
        }
        var photos = letterSourceLoader.photos(findings.stream().map(LetterContent.Source::reportId).distinct().toList());
        var letter = new Letter(UUID.randomUUID(), reference(), start, start.plusDays(6), actorId, clock.instant());
        var lines = LetterContent.compose(findings, photos);
        try {
            letterRepository.saveAndFlush(letter);
        } catch (DataIntegrityViolationException exception) {
            var draft = letterRepository.findFirstByPeriodStartAndStatus(start, Letter.DRAFT);
            if (draft.isPresent()) {
                return new Prepared(draft.get().getId(), false);
            }
            throw exception;
        }
        var position = 0;
        var stored = new java.util.ArrayList<LetterLine>();
        for (var line : lines) {
            stored.add(new LetterLine(
                    UUID.randomUUID(),
                    letter.getId(),
                    line.annex(),
                    position++,
                    line.reportId(),
                    line.reportReference(),
                    line.taxId(),
                    line.sellerName(),
                    line.decision(),
                    line.finding(),
                    line.proof(),
                    line.documentNumber(),
                    line.issuedOn(),
                    line.amount(),
                    line.justification(),
                    line.missingElements(),
                    line.dgiReference(),
                    line.photoId(),
                    line.contentType()));
        }
        letterLineRepository.saveAll(stored);
        return new Prepared(letter.getId(), true);
    }

    private String reference() {
        var year = clock.instant().atZone(KINSHASA).getYear();
        return "CO-" + year + "-" + String.format("%06d", letterRepository.nextNumber());
    }
}
