package cd.gouv.finances.signalements.report.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import cd.gouv.finances.signalements.report.ReportRejected;

import org.junit.jupiter.api.Test;

class DeclarantMessageTextTest {

    @Test
    void excludesTheReportReferenceFromTheMessage() {
        var body = DeclarantMessageText.body(DeclarantMessageText.SITUATION, "SIG-1");

        assertThat(body).isEqualTo("Votre signalement a été mis à jour. Consultez votre espace.");
        assertThat(body).doesNotContain("@", "SIG-1");
    }

    @Test
    void preparesAReplacementPhotoMessageWithATraceableReference() {
        assertThat(DeclarantMessageText.body(DeclarantMessageText.PHOTO,"NP-00000001"))
                .contains("NP-00000001","photo nette").hasSizeLessThanOrEqualTo(100);
    }

    @Test void boundsTheFinalSmsIncludingItsReferenceAndLiteralPercentSigns() {
        var reference = "12345678-1234-1234-1234-123456789abc";
        for (var kind : new String[] {"payout", "reexamination", "situation"}) {
            assertThat(DeclarantMessageText.body(kind, reference)).hasSizeLessThanOrEqualTo(100).doesNotContain(reference);
        }
        assertThat(DeclarantMessageText.render("x".repeat(64) + "%s", reference)).hasSize(100);
        assertThatThrownBy(() -> DeclarantMessageText.render("x".repeat(65) + "%s", reference))
                .isInstanceOf(ReportRejected.class).extracting(e -> ((ReportRejected)e).code()).isEqualTo("message-length");
        assertThat(DeclarantMessageText.render("100% reçu : %s", "SIG-1")).isEqualTo("100% reçu : SIG-1");
        assertThatThrownBy(() -> DeclarantMessageText.validatePattern("Bonjour %s %s", 36)).isInstanceOf(ReportRejected.class);
        assertThatThrownBy(() -> DeclarantMessageText.validatePattern("Bonjour\n%s", 36)).isInstanceOf(ReportRejected.class);
        assertThatThrownBy(() -> DeclarantMessageText.validatePattern("x".repeat(64) + "%s", 37)).isInstanceOf(ReportRejected.class);
    }

    @Test void dropsTheOldPlaceholderFromBothDefaultAndEditedTemplates() {
        var legacy = "La situation du signalement %s a été mise à jour.";
        assertThat(DeclarantMessageText.effectivePattern("situation", legacy, true)).startsWith("Votre signalement");
        assertThat(DeclarantMessageText.effectivePattern("situation", legacy, false)).isEqualTo("La situation du signalement a été mise à jour.");
        assertThat(DeclarantMessageText.effectivePattern("situation", "Bonjour %s", true)).isEqualTo("Bonjour");
    }
    @Test
    void removesAReportReferenceFromOldMessagesWithoutTouchingOtherNumbers() {
        assertThat(DeclarantMessageText.withoutReportReference("Prime (12) de 12000 FC.", "12"))
                .isEqualTo("Prime de 12000 FC.");
        assertThat(DeclarantMessageText.validateReportMessage("Votre dossier est disponible."))
                .isEqualTo("Votre dossier est disponible.");
        assertThatThrownBy(() -> DeclarantMessageText.validateReportMessage("Dossier %s"))
                .isInstanceOf(ReportRejected.class);
    }
}
