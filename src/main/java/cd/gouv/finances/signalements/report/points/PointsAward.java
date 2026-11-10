package cd.gouv.finances.signalements.report.points;

import cd.gouv.finances.signalements.report.ReportRejected;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class PointsAward {

    public static final String RULE = "cdc-2026-10-28";

    public static final Scale CDC = new Scale(4, 3, 3, 2, 1, 1, 5, 3);

    public record Scale(
            int fakeInvoice,
            int missingTransmission,
            int understated,
            int nonNormalized,
            int refusal,
            int bonus,
            int dailyCap,
            int spacingDays) {

        public Integer base(String finding) {
            return switch (finding) {
                case "fake-invoice" -> fakeInvoice;
                case "missing-transmission" -> missingTransmission;
                case "understated" -> understated;
                case "non-normalized" -> nonNormalized;
                case "refusal" -> refusal;
                default -> null;
            };
        }
    }

    private PointsAward() {
    }

    public record Claim(
            String decision,
            String finding,
            String taxId,
            String declarantId,
            boolean losingDuplicate,
            int earlierSameDay,
            LocalDate filedOn,
            List<LocalDate> creditedFilingDates,
            boolean establishmentAlreadyReported,
            int netPoints) {
    }

    public record Award(int base, int bonus) {
    }

    public static Award assess(Claim claim) {
        return assess(claim, CDC);
    }

    public static Award assess(Claim claim, Scale scale) {
        if (claim.filedOn() == null || claim.filedOn().isBefore(LocalDate.of(2026, 10, 1))
                || claim.filedOn().isAfter(LocalDate.of(2027, 4, 30))) {
            throw new ReportRejected("points-period", 409);
        }
        if (!"confirm".equals(claim.decision())) {
            throw new ReportRejected("points-qualification", 409);
        }
        var base = scale.base(claim.finding());
        if (base == null) {
            throw new ReportRejected("points-finding", 400);
        }
        if (blank(claim.taxId())) {
            throw new ReportRejected("points-tax", 400);
        }
        if (blank(claim.declarantId())) {
            throw new ReportRejected("points-declarant", 400);
        }
        if (claim.netPoints() > 0) {
            throw new ReportRejected("points-open", 409);
        }
        if (claim.losingDuplicate()) {
            throw new ReportRejected("points-duplicate", 409);
        }
        if (claim.earlierSameDay() >= scale.dailyCap()) {
            throw new ReportRejected("points-day", 409);
        }
        if (claim.creditedFilingDates().stream().anyMatch(other -> Math.abs(ChronoUnit.DAYS.between(other, claim.filedOn())) < scale.spacingDays())) {
            throw new ReportRejected("points-spacing", 409);
        }
        return new Award(base, claim.establishmentAlreadyReported() ? 0 : scale.bonus());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
