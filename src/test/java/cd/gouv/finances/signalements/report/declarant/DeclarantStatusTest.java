package cd.gouv.finances.signalements.report.declarant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeclarantStatusTest {

    @Test
    void keepsTheFurthestStatusThatIsAlreadyTrue() {
        assertThat(DeclarantStatus.of("pending", "queued", null)).isEqualTo(DeclarantStatus.RECEIVED);
        assertThat(DeclarantStatus.of("assigned", "legacy", null)).isEqualTo(DeclarantStatus.REVIEW);
        assertThat(DeclarantStatus.of("assigned", "legacy", "reject")).isEqualTo(DeclarantStatus.REJECTED);
        assertThat(DeclarantStatus.of("assigned", "legacy", "confirm")).isEqualTo(DeclarantStatus.CONFIRMED);
        assertThat(DeclarantStatus.of("pending", "validated", null)).isEqualTo(DeclarantStatus.CONFIRMED);
        assertThat(DeclarantStatus.of("pending", "rejected", null)).isEqualTo(DeclarantStatus.REJECTED);
        assertThat(DeclarantStatus.of("pending", "doubt", null)).isEqualTo(DeclarantStatus.REVIEW);
    }
}
