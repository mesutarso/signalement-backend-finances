package cd.gouv.finances.signalements.report.letter;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.LetterLine;
import cd.gouv.finances.signalements.report.repository.LetterLineRepository;
import cd.gouv.finances.signalements.report.repository.LetterRepository;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReadLetterAnnexQuery {

    private static final Set<String> SPREADSHEETS = Set.of(LetterLine.COMPANY, LetterLine.INVOICE);

    private final LetterRepository letterRepository;
    private final LetterLineRepository letterLineRepository;

    @PreAuthorize("hasAnyRole('SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public byte[] execute(UUID id, String annex) {
        if (!SPREADSHEETS.contains(annex)) {
            throw new ReportRejected("letter-annex", 400);
        }
        if (!letterRepository.existsById(id)) {
            throw new ReportRejected("letter-absent", 404);
        }
        return LetterAnnexDocument.csv(annex, letterLineRepository.findByLetterIdOrderByPositionAsc(id));
    }
}
