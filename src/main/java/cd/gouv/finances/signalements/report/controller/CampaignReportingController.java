package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.report.indicator.CampaignReporting;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CampaignReportingController {
    private final CampaignReporting reporting;
    public record Budget(@Positive @Max(1_000_000_000_000L) long amount, @NotBlank @Size(max = 160) String reference) { }
    public record Capture(@NotBlank String kind) { }
    @GetMapping("/api/campaign-budget")
    public CampaignReporting.Budget budget() { return reporting.budget(); }
    @PostMapping("/api/campaign-budget")
    public CampaignReporting.Budget record(@Valid @RequestBody Budget body, @AuthenticationPrincipal AccountUserDetails actor) {
        reporting.recordBudget(body.amount(), body.reference(), actor.getAccountId());
        return reporting.budget();
    }
    @GetMapping("/api/campaign-reports")
    public List<CampaignReporting.Report> reports() { return reporting.reports(); }
    @PostMapping("/api/campaign-reports")
    public List<CampaignReporting.Report> capture(@Valid @RequestBody Capture body, @AuthenticationPrincipal AccountUserDetails actor) {
        reporting.capture(body.kind(), actor.getAccountId());
        return reporting.reports();
    }
}
