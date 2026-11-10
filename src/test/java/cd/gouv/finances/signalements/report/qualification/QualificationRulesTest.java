package cd.gouv.finances.signalements.report.qualification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cd.gouv.finances.signalements.report.ReportRejected;
import org.junit.jupiter.api.Test;

class QualificationRulesTest {

    @Test
    void aPhotoCanRetainANonNormalizedInvoice() {
        var accepted = QualificationRules.accept("confirm", "non-normalized", "photo", false, null, null, false);

        assertThat(accepted.finding()).isEqualTo("non-normalized");
        assertThat(accepted.proof()).isEqualTo("photo");
    }

    @Test
    void aPhotoCannotRetainACaseThatNeedsTheRegister() {
        assertThatThrownBy(() -> QualificationRules.accept("confirm", "fake-invoice", "photo", false, null, null, false))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("qualification-proof");
    }

    @Test
    void rejectionKeepsNeitherFindingNorCorrection() {
        assertThatThrownBy(() -> QualificationRules.accept("reject", "non-normalized", null, false, null, null, false))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("qualification-finding");
        var accepted = QualificationRules.accept("reject", null, null, false, null, null, false);
        assertThat(accepted.decision()).isEqualTo("reject");
        assertThat(accepted.finding()).isNull();
    }

    @Test
    void aRefusalWaitsForTheAuthorityBeforeItCanBeRetained() {
        var waiting = QualificationRules.accept("wait", "refusal", "pending", false, "La pièce ne montre pas le refus.", null, false);
        assertThat(waiting.proof()).isEqualTo("pending");
        assertThatThrownBy(() -> QualificationRules.accept("confirm", "refusal", "authority", false, null, "DGI-1", false))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("qualification-waiting");
        var confirmed = QualificationRules.accept("confirm", "refusal", "authority", false, null, "DGI-1", true);
        assertThat(confirmed.reference()).isEqualTo("DGI-1");
    }

    @Test
    void aRefusalKeepsSellerEvidenceThroughTheAuthorityReview() {
        assertThat(QualificationRules.accept("wait", "refusal", "pending", true, "Confirmation DGI", null, false).decision())
                .isEqualTo("wait");
        assertThat(QualificationRules.accept("confirm", "refusal", "authority", true, null, "DGI-1", true).decision())
                .isEqualTo("confirm");
    }
}
