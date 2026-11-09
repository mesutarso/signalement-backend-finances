package cd.gouv.finances.signalements.report.reexamination;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReexaminationDeadlineTest {

    @Test
    void acceptsThirtyCalendarDaysFromTheDecision() {
        var decision = LocalDate.of(2026, 11, 1);
        assertThat(ReexaminationDeadline.onTime(decision, decision.plusDays(30))).isTrue();
        assertThat(ReexaminationDeadline.onTime(decision, decision.plusDays(31))).isFalse();
        assertThat(ReexaminationDeadline.onTime(decision, decision.minusDays(1))).isFalse();
        assertThat(ReexaminationDeadline.onTime(null, decision)).isFalse();
    }
}
