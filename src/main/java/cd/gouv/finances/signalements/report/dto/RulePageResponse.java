package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RulePageResponse(List<Version> versions) {

    public record Version(
            UUID id,
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
            List<RuleVersionBody.Tier> tiers,
            boolean active,
            Instant recordedAt) {
    }
}
