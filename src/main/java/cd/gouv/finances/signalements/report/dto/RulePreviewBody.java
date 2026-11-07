package cd.gouv.finances.signalements.report.dto;

import java.util.List;

public record RulePreviewBody(
        int fakeInvoice,
        int missingTransmission,
        int understated,
        int nonNormalized,
        int refusal,
        int bonus,
        int ceiling,
        int dailyCap,
        int spacingDays,
        List<RuleVersionBody.Tier> tiers,
        String finding,
        int netPoints,
        boolean firstEstablishment) {
}
