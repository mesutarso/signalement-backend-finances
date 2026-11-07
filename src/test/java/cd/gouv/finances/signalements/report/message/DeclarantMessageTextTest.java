package cd.gouv.finances.signalements.report.message;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeclarantMessageTextTest {

    @Test
    void mentionsOnlyTheReportReference() {
        var body = DeclarantMessageText.body(DeclarantMessageText.PHOTO, "SIG-1");

        assertThat(body).isEqualTo("Le signalement SIG-1 attend une nouvelle photo.");
        assertThat(body).doesNotContain("@");
    }
}
