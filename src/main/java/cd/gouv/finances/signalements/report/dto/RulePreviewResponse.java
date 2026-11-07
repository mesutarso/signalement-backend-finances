package cd.gouv.finances.signalements.report.dto;

import java.util.List;

public record RulePreviewResponse(int base, int bonus, int ceiling, List<RuleVersionBody.Tier> tiers) {
}
