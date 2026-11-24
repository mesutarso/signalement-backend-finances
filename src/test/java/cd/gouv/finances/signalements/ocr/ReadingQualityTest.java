package cd.gouv.finances.signalements.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReadingQualityTest {

    @Test
    void aCompleteDocumentIsReadable() {
        var fields = List.of(
                new ExtractedField("documentNumber", "F-12"),
                new ExtractedField("sellerTaxId", "A1234567B"),
                new ExtractedField("totalAmount", "15000"));

        assertThat(ReadingQuality.from(fields)).isEqualTo(ReadingQuality.READABLE);
    }

    @Test
    void aSingleUsefulFieldIsPartial() {
        assertThat(ReadingQuality.from(List.of(new ExtractedField("sellerName", "Kiosk"))))
                .isEqualTo(ReadingQuality.PARTIAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"vatAmount", "readCode", "defCode", "defNumber"})
    void aSingleDecisionFieldIsPartial(String field) {
        assertThat(ReadingQuality.from(List.of(new ExtractedField(field, "16"))))
                .isEqualTo(ReadingQuality.PARTIAL);
    }

    @Test
    void noUsefulFieldIsUnreadable() {
        assertThat(ReadingQuality.from(List.of(new ExtractedField("documentNumber", "  "))))
                .isEqualTo(ReadingQuality.UNREADABLE);
    }
}
