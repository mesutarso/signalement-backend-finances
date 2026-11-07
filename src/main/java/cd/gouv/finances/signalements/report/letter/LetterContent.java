package cd.gouv.finances.signalements.report.letter;

import cd.gouv.finances.signalements.report.entity.LetterLine;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LetterContent {

    private LetterContent() {
    }

    public record Source(
            UUID reportId,
            String reportReference,
            String taxId,
            String sellerName,
            String decision,
            String finding,
            String proof,
            String documentNumber,
            LocalDate issuedOn,
            String amount,
            String justification,
            String missingElements,
            String dgiReference) {
    }

    public record Photo(UUID reportId, UUID photoId, String contentType) {
    }

    public record Line(
            String annex,
            UUID reportId,
            String reportReference,
            String taxId,
            String sellerName,
            String decision,
            String finding,
            String proof,
            String documentNumber,
            LocalDate issuedOn,
            String amount,
            String justification,
            String missingElements,
            String dgiReference,
            UUID photoId,
            String contentType) {
    }

    public static List<Line> compose(List<Source> sources, List<Photo> photos) {
        var lines = new ArrayList<Line>();
        for (var source : sources) {
            var taxId = blankToNull(source.taxId());
            if (taxId == null) {
                continue;
            }
            lines.add(line(LetterLine.COMPANY, source, taxId, null, null, null, null, null, null, null, null, null));
            if (blankToNull(source.documentNumber()) != null || source.issuedOn() != null || blankToNull(source.amount()) != null) {
                lines.add(line(LetterLine.INVOICE, source, taxId, null, null, null, blankToNull(source.documentNumber()), source.issuedOn(), blankToNull(source.amount()), null, null, null));
            }
            lines.add(line(LetterLine.SHEET, source, taxId, source.decision(), null, null, null, null, null, source.justification(), blankToNull(source.missingElements()), blankToNull(source.dgiReference())));
            for (var photo : photos) {
                if (source.reportId().equals(photo.reportId())) {
                    lines.add(line(LetterLine.PIECE, source, taxId, null, null, null, null, null, null, null, null, null, photo.photoId(), blankToNull(photo.contentType())));
                }
            }
        }
        return lines;
    }

    private static Line line(
            String annex,
            Source source,
            String taxId,
            String decision,
            String finding,
            String proof,
            String documentNumber,
            LocalDate issuedOn,
            String amount,
            String justification,
            String missingElements,
            String dgiReference,
            UUID photoId,
            String contentType) {
        return new Line(annex, source.reportId(), source.reportReference(), taxId, blankToNull(source.sellerName()), decision, finding, proof, documentNumber, issuedOn, amount, justification, missingElements, dgiReference, photoId, contentType);
    }

    private static Line line(
            String annex,
            Source source,
            String taxId,
            String decision,
            String finding,
            String proof,
            String documentNumber,
            LocalDate issuedOn,
            String amount,
            String justification,
            String missingElements,
            String dgiReference) {
        var keptDecision = LetterLine.COMPANY.equals(annex) || LetterLine.SHEET.equals(annex) ? source.decision() : decision;
        var keptFinding = LetterLine.COMPANY.equals(annex) || LetterLine.INVOICE.equals(annex) ? source.finding() : finding;
        var keptProof = LetterLine.COMPANY.equals(annex) ? source.proof() : proof;
        return line(annex, source, taxId, keptDecision, keptFinding, keptProof, documentNumber, issuedOn, amount, justification, missingElements, dgiReference, null, null);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
