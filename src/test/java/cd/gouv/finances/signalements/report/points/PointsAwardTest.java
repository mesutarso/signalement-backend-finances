package cd.gouv.finances.signalements.report.points;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.points.PointsAward.Claim;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PointsAwardTest {

    private final LocalDate filed = LocalDate.parse("2026-11-04");

    @Test
    void aConfirmedNormalizedInvoiceEarnsTwoPointsAndTheFirstEstablishmentBonus() {
        var award = PointsAward.assess(claim("confirm", "non-normalized", "A-1", "user-1", false, 0, List.of(), false, 0));

        assertThat(award.base()).isEqualTo(2);
        assertThat(award.bonus()).isEqualTo(1);
    }

    @Test
    void anUnknownSellerAndASixthReportOfTheDayEarnNothing() {
        assertThatThrownBy(() -> PointsAward.assess(claim("confirm", "unknown-seller", "A-1", "user-1", false, 0, List.of(), false, 0)))
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("points-finding");
        assertThatThrownBy(() -> PointsAward.assess(claim("confirm", "refusal", "A-1", "user-1", false, 5, List.of(), true, 0)))
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("points-day");
    }

    @Test
    void threeDaysMustSeparateTwoCreditsForTheSameEstablishment() {
        assertThatThrownBy(() -> PointsAward.assess(claim("confirm", "understated", "A-1", "user-1", false, 0, List.of(filed.minusDays(2)), true, 0)))
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("points-spacing");
        var award = PointsAward.assess(claim("confirm", "understated", "A-1", "user-1", false, 0, List.of(filed.minusDays(3)), true, 0));

        assertThat(award.base()).isEqualTo(3);
        assertThat(award.bonus()).isZero();
    }

    @Test
    void aLaterDailyLimitAppliesOnlyToTheClaimBeingAssessed() {
        var wider = new PointsAward.Scale(4, 3, 3, 2, 1, 0, 6, 3);

        var award = PointsAward.assess(claim("confirm", "refusal", "A-1", "user-1", false, 5, List.of(), false, 0), wider);

        assertThat(award.base()).isEqualTo(1);
        assertThat(award.bonus()).isZero();
    }

    @Test
    void aLosingDuplicateDoesNotEarnPoints() {
        assertThatThrownBy(() -> PointsAward.assess(claim("confirm", "fake-invoice", "A-1", "user-1", true, 0, List.of(), false, 0)))
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("points-duplicate");
    }

    private Claim claim(
            String decision,
            String finding,
            String taxId,
            String declarantId,
            boolean losingDuplicate,
            int earlierSameDay,
            List<LocalDate> credited,
            boolean alreadyReported,
            int net) {
        return new Claim(decision, finding, taxId, declarantId, losingDuplicate, earlierSameDay, filed, credited, alreadyReported, net);
    }
}
