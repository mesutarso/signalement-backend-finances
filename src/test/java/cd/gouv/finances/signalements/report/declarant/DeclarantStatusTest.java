package cd.gouv.finances.signalements.report.declarant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeclarantStatusTest {

    @Test
    void keepsTheFurthestStatusThatIsAlreadyTrue() {
        assertThat(DeclarantStatus.of("pending", null)).isEqualTo(DeclarantStatus.RECEIVED);
        assertThat(DeclarantStatus.of("assigned", null)).isEqualTo(DeclarantStatus.REVIEW);
        assertThat(DeclarantStatus.of("assigned", "reject")).isEqualTo(DeclarantStatus.REJECTED);
        assertThat(DeclarantStatus.of("assigned", "confirm")).isEqualTo(DeclarantStatus.CONFIRMED);
    }
}
