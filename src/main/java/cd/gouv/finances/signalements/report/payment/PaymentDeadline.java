package cd.gouv.finances.signalements.report.payment;

import java.time.DayOfWeek;
import java.time.LocalDate;

public final class PaymentDeadline {

    private PaymentDeadline() {
    }

    public static LocalDate dueOn(LocalDate authorizedOn) {
        var date = authorizedOn;
        var remaining = 10;
        while (remaining > 0) {
            date = date.plusDays(1);
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                remaining--;
            }
        }
        return date;
    }
}
