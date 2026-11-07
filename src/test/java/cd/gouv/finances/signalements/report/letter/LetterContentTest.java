package cd.gouv.finances.signalements.report.letter;

import static org.assertj.core.api.Assertions.assertThat;

import cd.gouv.finances.signalements.report.entity.LetterLine;
import cd.gouv.finances.signalements.report.letter.LetterContent.Photo;
import cd.gouv.finances.signalements.report.letter.LetterContent.Source;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LetterContentTest {

    @Test
    void aRetainedFindingKeepsCompanySheetAndInvoiceApartFromThePicture() {
        var reportId = UUID.randomUUID();
        var source = source(reportId, "A-1", "FAC-9", LocalDate.parse("2026-10-02"), "1500");
        var photo = new Photo(reportId, UUID.randomUUID(), "image/jpeg");

        var lines = LetterContent.compose(List.of(source), List.of(photo));

        assertThat(lines).extracting(LetterContent.Line::annex)
                .containsExactly(LetterLine.COMPANY, LetterLine.INVOICE, LetterLine.SHEET, LetterLine.PIECE);
        assertThat(lines.get(1).documentNumber()).isEqualTo("FAC-9");
        assertThat(lines.get(3).photoId()).isEqualTo(photo.photoId());
        assertThat(lines.get(3).contentType()).isEqualTo("image/jpeg");
        assertThat(lines).allSatisfy(line -> assertThat(line.reportReference()).isEqualTo("REF-1"));
    }

    @Test
    void aFindingWithoutAnInvoiceDoesNotInventOne() {
        var lines = LetterContent.compose(List.of(source(UUID.randomUUID(), "B-2", null, null, " ")), List.of());

        assertThat(lines).extracting(LetterContent.Line::annex).containsExactly(LetterLine.COMPANY, LetterLine.SHEET);
    }

    private static Source source(UUID reportId, String taxId, String number, LocalDate issuedOn, String amount) {
        return new Source(reportId, "REF-1", taxId, "Kiosque", "confirm", "non-normalized", "register", number, issuedOn, amount, "Constat retenu.", null, null);
    }
}
