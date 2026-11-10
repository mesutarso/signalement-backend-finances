package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.dto.RulePageResponse;
import cd.gouv.finances.signalements.report.dto.RulePreviewBody;
import cd.gouv.finances.signalements.report.dto.RulePreviewResponse;
import cd.gouv.finances.signalements.report.dto.RuleVersionBody;
import cd.gouv.finances.signalements.report.rule.ActivateRuleVersionCommand;
import cd.gouv.finances.signalements.report.rule.PreviewRuleCommand;
import cd.gouv.finances.signalements.report.rule.ReadRuleVersionsQuery;
import cd.gouv.finances.signalements.report.rule.RecordRuleVersionCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Rules")
@SecurityRequirement(name = "auth")
public class RuleController {

    private final ReadRuleVersionsQuery readRuleVersionsQuery;
    private final PreviewRuleCommand previewRuleCommand;
    private final RecordRuleVersionCommand recordRuleVersionCommand;
    private final ActivateRuleVersionCommand activateRuleVersionCommand;
    private final RecordAudit recordAudit;

    @GetMapping("/api/rules")
    @Operation(summary = "Lists the rule versions without mixing their points")
    public RulePageResponse list() {
        return readRuleVersionsQuery.execute();
    }

    @PostMapping("/api/rules/previews")
    @Operation(summary = "Previews a rule version without saving it")
    public RulePreviewResponse preview(@RequestBody RulePreviewBody body) {
        return previewRuleCommand.execute(body);
    }

    @PostMapping("/api/rules")
    @Operation(summary = "Records an inactive rule version for later activation")
    public RulePageResponse record(@RequestBody RuleVersionBody body, @AuthenticationPrincipal AccountUserDetails account) {
        var id = recordRuleVersionCommand.execute(body, account.getAccountId());
        recordAudit.allowed(account.getAccountId(), "rule-version", "rule", id.toString());
        return readRuleVersionsQuery.execute();
    }

    @PostMapping("/api/rules/{id}/activation")
    @Operation(summary = "Activates a rule version for reports filed after its activation")
    public RulePageResponse activate(@PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails account) {
        activateRuleVersionCommand.execute(id);
        recordAudit.allowed(account.getAccountId(), "rule-activation", "rule", id.toString());
        return readRuleVersionsQuery.execute();
    }
}
