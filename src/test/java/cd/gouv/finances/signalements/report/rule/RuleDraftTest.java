package cd.gouv.finances.signalements.report.rule;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cd.gouv.finances.signalements.report.ReportRejected;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleDraftTest {

    @Test
    void rejectsABonusOtherThanZeroOrOneAndTiersThatDoNotRise() {
        assertThatThrownBy(() -> RuleDraft.require("cdc-next", 4, 3, 3, 2, 1, 2, 1_000_000, 5, 3, List.of(new RuleDraft.Tier(30, 10_000))))
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("rule-bonus");
        assertThatThrownBy(() -> RuleDraft.require("cdc-next", 4, 3, 3, 2, 1, 1, 1_000_000, 5, 3, List.of(
                new RuleDraft.Tier(60, 10_000),
                new RuleDraft.Tier(30, 10_000))))
                .extracting(error -> ((ReportRejected) error).code())
                .isEqualTo("rule-tier");
    }
}
