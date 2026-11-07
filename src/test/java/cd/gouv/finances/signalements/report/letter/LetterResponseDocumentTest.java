package cd.gouv.finances.signalements.report.letter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cd.gouv.finances.signalements.report.ReportRejected;
import org.junit.jupiter.api.Test;

class LetterResponseDocumentTest {

    @Test
    void aPdfIsAcceptedAndAnotherFileIsRefused() {
        var accepted = LetterResponseDocument.accept("%PDF-1.7".getBytes());

        assertThat(accepted.contentType()).isEqualTo("application/pdf");
        assertThat(accepted.fingerprint()).hasSize(64);
        assertThatThrownBy(() -> LetterResponseDocument.accept("texte".getBytes()))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("response-type");
    }
}
