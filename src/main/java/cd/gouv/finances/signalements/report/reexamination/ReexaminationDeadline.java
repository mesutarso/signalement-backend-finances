package cd.gouv.finances.signalements.report.reexamination;

import java.time.LocalDate;

public final class ReexaminationDeadline {

    public static final int DAYS = 30;

    private ReexaminationDeadline() {
    }

    public static boolean onTime(LocalDate decisionOn, LocalDate receivedOn) {
        if (decisionOn == null || receivedOn == null) {
            return false;
        }
        return !receivedOn.isBefore(decisionOn) && !receivedOn.isAfter(decisionOn.plusDays(DAYS));
    }
}
