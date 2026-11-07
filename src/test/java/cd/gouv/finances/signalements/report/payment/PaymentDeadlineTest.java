package cd.gouv.finances.signalements.report.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PaymentDeadlineTest {

    @Test
    void countsTenWeekdaysAfterAFriday() {
        assertThat(PaymentDeadline.dueOn(LocalDate.parse("2026-11-06"))).isEqualTo(LocalDate.parse("2026-11-20"));
    }

    @Test
    void countsTenWeekdaysAfterAMonday() {
        assertThat(PaymentDeadline.dueOn(LocalDate.parse("2026-11-02"))).isEqualTo(LocalDate.parse("2026-11-16"));
    }
}
