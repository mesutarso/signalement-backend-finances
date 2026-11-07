package cd.gouv.finances.signalements.report.prime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PrimeScaleTest {

    @Test
    void crossesTheFirstThreeThresholdsAndKeepsTheSurplus() {
        assertThat(PrimeScale.reached(29)).isEmpty();
        assertThat(PrimeScale.surplus(29)).isEqualTo(29);

        assertThat(PrimeScale.reached(30)).containsExactly(new PrimeScale.Tier(30, 10_000));
        assertThat(PrimeScale.surplus(30)).isZero();

        assertThat(PrimeScale.reached(59)).containsExactly(new PrimeScale.Tier(30, 10_000));
        assertThat(PrimeScale.surplus(59)).isEqualTo(29);

        assertThat(PrimeScale.reached(60)).containsExactly(new PrimeScale.Tier(30, 10_000), new PrimeScale.Tier(60, 20_000));
        assertThat(PrimeScale.surplus(99)).isEqualTo(39);

        assertThat(PrimeScale.reached(100)).containsExactly(
                new PrimeScale.Tier(30, 10_000),
                new PrimeScale.Tier(60, 20_000),
                new PrimeScale.Tier(100, 30_000));
        assertThat(PrimeScale.granted(100)).isEqualTo(60_000);
        assertThat(PrimeScale.surplus(149)).isEqualTo(49);
        assertThat(PrimeScale.reached(150)).hasSize(4);
    }

    @Test
    void stopsBeforeTheCeilingAndKeepsTheRemainingPoints() {
        var reached = PrimeScale.reached(10_000);
        assertThat(PrimeScale.granted(10_000)).isEqualTo(990_000);
        assertThat(reached.get(reached.size() - 1).threshold()).isEqualTo(1650);
        assertThat(PrimeScale.surplus(10_000)).isEqualTo(8_350);
        assertThat(PrimeScale.granted(1650)).isEqualTo(990_000);
        assertThat(PrimeScale.reached(1649)).hasSize(reached.size() - 1);
    }
}
