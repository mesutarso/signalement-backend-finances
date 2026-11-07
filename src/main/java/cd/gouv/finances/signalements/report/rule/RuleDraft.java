package cd.gouv.finances.signalements.report.rule;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.points.PointsAward;
import cd.gouv.finances.signalements.report.prime.PrimeScale;
import java.util.ArrayList;
import java.util.List;

public final class RuleDraft {

    private static final int TIER_LIMIT = 80;

    private RuleDraft() {
    }

    public record Tier(int threshold, int amount) {
    }

    public record Draft(String code, PointsAward.Scale scale, int ceiling, List<PrimeScale.Tier> tiers) {
    }

    public static Draft require(
            String code,
            int fakeInvoice,
            int missingTransmission,
            int understated,
            int nonNormalized,
            int refusal,
            int bonus,
            int ceiling,
            int dailyCap,
            int spacingDays,
            List<Tier> tiers) {
        var kept = code == null ? "" : code.trim();
        if (!kept.matches("[a-z0-9](?:[a-z0-9-]{0,30}[a-z0-9])?")) {
            throw new ReportRejected("rule-code", 400);
        }
        if (fakeInvoice < 1 || missingTransmission < 1 || understated < 1 || nonNormalized < 1 || refusal < 1
                || ceiling < 1) {
            throw new ReportRejected("rule-scale", 400);
        }
        if (bonus != 0 && bonus != 1) {
            throw new ReportRejected("rule-bonus", 400);
        }
        if (dailyCap < 1 || spacingDays < 1) {
            throw new ReportRejected("rule-limit", 400);
        }
        if (tiers == null || tiers.isEmpty() || tiers.size() > TIER_LIMIT) {
            throw new ReportRejected("rule-tier", 400);
        }
        var keptTiers = new ArrayList<PrimeScale.Tier>();
        var previous = 0;
        for (var tier : tiers) {
            if (tier.threshold() <= previous || tier.amount() < 1 || tier.amount() > ceiling) {
                throw new ReportRejected("rule-tier", 400);
            }
            previous = tier.threshold();
            keptTiers.add(new PrimeScale.Tier(tier.threshold(), tier.amount()));
        }
        return new Draft(
                kept,
                new PointsAward.Scale(fakeInvoice, missingTransmission, understated, nonNormalized, refusal, bonus, dailyCap, spacingDays),
                ceiling,
                List.copyOf(keptTiers));
    }
}
