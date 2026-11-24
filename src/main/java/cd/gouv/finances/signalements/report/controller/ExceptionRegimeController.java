package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.payment.ExceptionalPaymentBatches;
import cd.gouv.finances.signalements.report.payment.ReadExceptionRegime;
import cd.gouv.finances.signalements.report.validation.ExceptionalAssessment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exception-regime")
@PreAuthorize("hasRole('ADMINISTRATOR')")
@RequiredArgsConstructor
public class ExceptionRegimeController {
    private final ReadExceptionRegime reports;
    private final ExceptionalAssessment assessment;
    private final ExceptionalPaymentBatches payments;

    public record Validation(LocalDate start, LocalDate end, @NotBlank @Size(max = 500) String reason) { }
    public record Validated(int validated, int skipped) { }

    @GetMapping
    public ReadExceptionRegime.Page list(@RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end, @RequestParam(defaultValue = "0") int page) {
        return reports.page(new ReadExceptionRegime.Period(start, end), page);
    }

    @GetMapping("/payment-candidates")
    public List<ReadExceptionRegime.Candidate> candidates(@RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end) {
        return reports.candidates(new ReadExceptionRegime.Period(start, end));
    }

    @PostMapping("/validations")
    public Validated validate(@Valid @RequestBody Validation body, @AuthenticationPrincipal AccountUserDetails account) {
        var pending = reports.pendingValidation(new ReadExceptionRegime.Period(body.start(), body.end()));
        int changed = 0;
        for (var report : pending) {
            try {
                if (assessment.validate(report, body.reason().strip(), account.getAccountId())) changed++;
            } catch (ReportRejected blocked) {
                if (!"payment-hold".equals(blocked.code()) && !"assessment-absent".equals(blocked.code())) throw blocked;
            }
        }
        return new Validated(changed, pending.size() - changed);
    }

    @PostMapping("/payments")
    public ExceptionalPaymentBatches.Batch pay(@Valid @RequestBody ExceptionalPaymentBatches.Request request,
            @AuthenticationPrincipal AccountUserDetails account) {
        return payments.authorize(request, account.getAccountId());
    }
}
