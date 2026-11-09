package cd.gouv.finances.signalements.report.prime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PrimeLossTest {

    @Test
    void losesThePrimeTheDayAfterNinetyCalendarDaysWithoutASingleNumber() {
        var credit = LocalDate.of(2026, 1, 1);
        assertThat(PrimeLoss.due(credit, credit.plusDays(90), false, false)).isFalse();
        assertThat(PrimeLoss.due(credit, credit.plusDays(91), false, false)).isTrue();
    }

    @Test
    void keepsAPrimeThatAlreadyHasASingleNumber() {
        var credit = LocalDate.of(2026, 1, 1);
        assertThat(PrimeLoss.due(credit, credit.plusDays(120), true, false)).isFalse();
    }

    @Test
    void staysLostAfterItHasBeenMarked() {
        assertThat(PrimeLoss.due(null, LocalDate.of(2026, 11, 7), true, true)).isTrue();
    }
}
