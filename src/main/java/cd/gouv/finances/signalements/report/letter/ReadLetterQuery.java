package cd.gouv.finances.signalements.report.letter;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.dto.LetterDetailResponse;
import cd.gouv.finances.signalements.report.dto.LetterLineResponse;
import cd.gouv.finances.signalements.report.dto.LetterSummaryResponse;
import cd.gouv.finances.signalements.report.dto.LetterResponseSummary;
import cd.gouv.finances.signalements.report.entity.Letter;
import cd.gouv.finances.signalements.report.entity.LetterLine;
import cd.gouv.finances.signalements.report.repository.LetterLineRepository;
import cd.gouv.finances.signalements.report.repository.LetterRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReadLetterQuery {

    private final LetterRepository letterRepository;
    private final LetterLineRepository letterLineRepository;
    private final JdbcTemplate jdbc;

    @PreAuthorize("hasAnyRole('SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public LetterDetailResponse execute(UUID id) {
        var letter = letterRepository.findById(id).orElseThrow(() -> new ReportRejected("letter-absent", 404));
        var lines = letterLineRepository.findByLetterIdOrderByPositionAsc(id).stream().map(ReadLetterQuery::line).toList();
        var responses = jdbc.query("""
                select id, received_on, service, reference_code, conclusion, content_type, recorded_at
                from app.letter_response
                where letter_id = ?
                order by recorded_at desc
                """, (row, number) -> new LetterResponseSummary(
                row.getObject(1, UUID.class),
                row.getDate(2).toLocalDate(),
                row.getString(3),
                row.getString(4),
                row.getString(5),
                row.getString(6),
                row.getTimestamp(7).toInstant()), id);
        return new LetterDetailResponse(summary(letter), lines, responses);
    }

    static LetterSummaryResponse summary(Letter letter) {
        return new LetterSummaryResponse(
                letter.getId(),
                letter.getReferenceCode(),
                letter.getPeriodStart(),
                letter.getPeriodEnd(),
                letter.getStatus(),
                letter.getRecipient(),
                letter.getReason(),
                letter.getPreparedAt(),
                letter.getDecidedAt());
    }

    private static LetterLineResponse line(LetterLine line) {
        return new LetterLineResponse(
                line.getId(),
                line.getAnnex(),
                line.getReportId(),
                line.getReportReference(),
                line.getTaxId(),
                line.getSellerName(),
                line.getDecision(),
                line.getFinding(),
                line.getProof(),
                line.getDocumentNumber(),
                line.getIssuedOn(),
                line.getAmount(),
                line.getJustification(),
                line.getMissingElements(),
                line.getDgiReference(),
                line.getPhotoId(),
                line.getContentType());
    }
}
