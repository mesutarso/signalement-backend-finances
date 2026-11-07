package cd.gouv.finances.signalements.report.sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentSampleSelectionTest {

    @Test
    void roundsFivePercentUpAndSpreadsTheIndexes() {
        assertThat(PaymentSampleSelection.size(1)).isEqualTo(1);
        assertThat(PaymentSampleSelection.size(20)).isEqualTo(1);
        assertThat(PaymentSampleSelection.size(21)).isEqualTo(2);
        assertThat(PaymentSampleSelection.indexes(21)).containsExactly(0, 10);
    }
}
