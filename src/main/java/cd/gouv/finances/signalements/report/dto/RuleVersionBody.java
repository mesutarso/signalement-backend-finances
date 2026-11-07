package cd.gouv.finances.signalements.report.dto;

import java.util.List;

public record RuleVersionBody(
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

    public record Tier(int threshold, int amount) {
    }
}
