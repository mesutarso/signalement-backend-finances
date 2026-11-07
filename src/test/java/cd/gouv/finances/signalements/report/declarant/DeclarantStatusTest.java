package cd.gouv.finances.signalements.report.declarant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeclarantStatusTest {

    @Test
    void keepsTheFurthestStatusThatIsAlreadyTrue() {
        assertThat(DeclarantStatus.of("pending", null, 0)).isEqualTo(DeclarantStatus.RECEIVED);
        assertThat(DeclarantStatus.of("assigned", null, 0)).isEqualTo(DeclarantStatus.REVIEW);
        assertThat(DeclarantStatus.of("assigned", "reject", 0)).isEqualTo(DeclarantStatus.REJECTED);
        assertThat(DeclarantStatus.of("assigned", "confirm", 0)).isEqualTo(DeclarantStatus.CONFIRMED);
        assertThat(DeclarantStatus.of("assigned", "confirm", 4)).isEqualTo(DeclarantStatus.POINTS);
    }
}
