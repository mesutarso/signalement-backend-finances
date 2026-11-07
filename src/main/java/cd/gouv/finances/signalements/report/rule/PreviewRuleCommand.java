package cd.gouv.finances.signalements.report.rule;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.dto.RulePreviewBody;
import cd.gouv.finances.signalements.report.dto.RulePreviewResponse;
import cd.gouv.finances.signalements.report.dto.RuleVersionBody;
import cd.gouv.finances.signalements.report.points.PointsAward;
import cd.gouv.finances.signalements.report.prime.PrimeScale;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component
public class PreviewRuleCommand {

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    public RulePreviewResponse execute(RulePreviewBody body) {
        if (body.netPoints() < 0) {
            throw new ReportRejected("rule-sample", 400);
        }
        var tiers = body.tiers() == null ? List.<RuleVersionBody.Tier>of() : body.tiers();
        var draft = RuleDraft.require(
                "preview",
                body.fakeInvoice(),
                body.missingTransmission(),
                body.understated(),
                body.nonNormalized(),
                body.refusal(),
                body.bonus(),
                body.ceiling(),
                body.dailyCap(),
                body.spacingDays(),
                tiers.stream().map(tier -> new RuleDraft.Tier(tier.threshold(), tier.amount())).toList());
        var base = draft.scale().base(body.finding());
        if (base == null) {
            throw new ReportRejected("rule-finding", 400);
        }
        var award = PointsAward.assess(new PointsAward.Claim(
                "confirm",
                body.finding(),
                "preview",
                "preview",
                false,
                0,
                LocalDate.of(2026, 11, 1),
                List.of(),
                !body.firstEstablishment(),
                0), draft.scale());
        var reached = PrimeScale.reached(body.netPoints(), draft.ceiling(), draft.tiers()).stream()
                .map(tier -> new RuleVersionBody.Tier(tier.threshold(), tier.amount()))
                .toList();
        return new RulePreviewResponse(award.base(), award.bonus(), draft.ceiling(), reached);
    }
}
