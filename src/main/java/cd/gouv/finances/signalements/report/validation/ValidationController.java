package cd.gouv.finances.signalements.report.validation;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ValidationController {
    private final AssessmentStore store;
    private final ReviewAssessment review;
    private final AnalysisJobs jobs;
    private final RewardSettings settings;

    @GetMapping("/api/reports/{id}/assessment")
    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    public AssessmentStore.View one(@PathVariable UUID id) { return store.view(id); }

    @PostMapping("/api/reports/{id}/assessment")
    public AssessmentStore.View review(@PathVariable UUID id, @Valid @RequestBody AssessmentReview body,
                                       @AuthenticationPrincipal AccountUserDetails account) {
        review.execute(id, body, account.getAccountId());
        return store.view(id);
    }

    @PostMapping("/api/reports/{id}/analysis")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    public AssessmentStore.View analyze(@PathVariable UUID id) {
        store.read(id);
        jobs.enqueue(id, false);
        return store.view(id);
    }

    @GetMapping("/api/reward-settings")
    public RewardSettings.Setting settings() { return settings.read(); }

    public record SettingChange(@Min(1) int amount, @NotNull UUID expectedId) { }

    @PostMapping("/api/reward-settings")
    public RewardSettings.Setting change(@Valid @RequestBody SettingChange body,
                                              @AuthenticationPrincipal AccountUserDetails account) {
        settings.change(body.amount(), body.expectedId(), account.getAccountId());
        return settings.read();
    }
}
