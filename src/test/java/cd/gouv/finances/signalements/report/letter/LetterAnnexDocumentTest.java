package cd.gouv.finances.signalements.report.letter;

import static org.assertj.core.api.Assertions.assertThat;

import cd.gouv.finances.signalements.report.entity.LetterLine;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LetterAnnexDocumentTest {

    @Test
    void theCompanyAnnexQuotesTextAndNeutralizesAFormula() {
        var line = new LetterLine(UUID.randomUUID(), UUID.randomUUID(), LetterLine.COMPANY, 0, UUID.randomUUID(), "REF-1", "A-1", "=Kiosque", "confirm", "non-normalized", "register", null, null, null, null, null, null, null, null);
        var csv = new String(LetterAnnexDocument.csv(LetterLine.COMPANY, List.of(line)), StandardCharsets.UTF_8);

        assertThat(csv).contains("Numéro d'impôt");
        assertThat(csv).contains("'=Kiosque");
        assertThat(csv).contains("Facture non normalisée");
        assertThat(csv).doesNotContain("phone");
        assertThat(csv).doesNotContain("email");
    }
}
