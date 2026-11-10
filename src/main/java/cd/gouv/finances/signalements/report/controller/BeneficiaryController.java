package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.prime.BeneficiaryRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class BeneficiaryController {
    private final BeneficiaryRegistry registry;
    private final RecordAudit audit;

    public record Verification(@NotBlank @Size(max = 80) String reference,
            @NotBlank @Size(min = 3, max = 80) String beneficiaryReference,
            @NotBlank @Size(max = 160) String evidence) { }

    @GetMapping("/api/beneficiaries")
    public BeneficiaryRegistry.Situation read(@RequestParam String reference) {
        return registry.read(reference);
    }

    @PostMapping("/api/beneficiaries")
    public BeneficiaryRegistry.Situation verify(@Valid @RequestBody Verification body,
            @AuthenticationPrincipal AccountUserDetails account) {
        registry.verify(body.reference(), body.beneficiaryReference(), body.evidence(), account.getAccountId());
        audit.allowed(account.getAccountId(), "beneficiary-verification", "beneficiary", body.reference());
        return registry.read(body.reference());
    }
}
