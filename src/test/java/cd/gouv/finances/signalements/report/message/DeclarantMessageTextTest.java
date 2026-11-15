package cd.gouv.finances.signalements.report.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import cd.gouv.finances.signalements.report.ReportRejected;

import org.junit.jupiter.api.Test;

class DeclarantMessageTextTest {

    @Test
    void mentionsOnlyTheReportReference() {
        var body = DeclarantMessageText.body(DeclarantMessageText.SITUATION, "SIG-1");

        assertThat(body).isEqualTo("La situation du signalement SIG-1 a été mise à jour.");
        assertThat(body).doesNotContain("@");
    }

    @Test
    void refusesReplacementPhotoMessages() {
        assertThatThrownBy(() -> DeclarantMessageText.body(DeclarantMessageText.PHOTO, "SIG-1"))
                .isInstanceOf(ReportRejected.class)
                .extracting(error -> ((ReportRejected) error).code()).isEqualTo("photo-request-retired");
    }
}
