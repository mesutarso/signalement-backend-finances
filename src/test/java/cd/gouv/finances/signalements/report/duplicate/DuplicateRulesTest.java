package cd.gouv.finances.signalements.report.duplicate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DuplicateRulesTest {

    @Test
    void keepsTheEarlierCompleteReport() {
        var early = new DuplicatePriority.Side(
                UUID.randomUUID(), "early", LocalDateTime.parse("2026-10-01T10:00:00"), "b", true);
        var late = new DuplicatePriority.Side(
                UUID.randomUUID(), "late", LocalDateTime.parse("2026-10-02T10:00:00"), "a", false);

        var winner = DuplicatePriority.choose(late, early);

        assertThat(winner).isEqualTo(early);
        assertThat(DuplicatePriority.basis(winner, late)).isEqualTo("complete");
    }

    @Test
    void usesTheReferenceWhenBothArriveTogetherAndStayIncomplete() {
        var left = new DuplicatePriority.Side(
                UUID.randomUUID(), "left", LocalDateTime.parse("2026-10-01T10:00:00"), "b", false);
        var right = new DuplicatePriority.Side(
                UUID.randomUUID(), "right", LocalDateTime.parse("2026-10-01T10:00:00"), "a", false);

        var winner = DuplicatePriority.choose(left, right);

        assertThat(winner.identifier()).isEqualTo("right");
        assertThat(DuplicatePriority.basis(winner, left)).isEqualTo("reference");
    }

    @Test
    void aSharedNumberWithoutTheSameTaxIdStaysPossible() {
        var self = new DuplicateSignals.Facts("FAC-1", "A1", null);
        var otherId = UUID.randomUUID();
        var matches = DuplicateSignals.compare(self, Map.of(otherId, new DuplicateSignals.Facts("fac-1", "B2", null)));

        assertThat(matches).singleElement().satisfies(match -> {
            assertThat(match.otherReportId()).isEqualTo(otherId);
            assertThat(match.criterion()).isEqualTo("number");
            assertThat(match.strength()).isEqualTo("possible");
        });
    }

    @Test
    void theSameNumberAndTaxIdIsALikelyInvoice() {
        var otherId = UUID.randomUUID();
        var matches = DuplicateSignals.compare(
                new DuplicateSignals.Facts("FAC-1", "A1", "CODE-9"),
                Map.of(otherId, new DuplicateSignals.Facts("FAC-1", "A1", "CODE-9")));

        assertThat(matches).extracting(DuplicateSignals.Match::criterion).containsExactly("invoice", "code");
        assertThat(matches).allMatch(match -> match.strength().equals("likely"));
    }
}
