package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.dto.PointCreditBody;
import cd.gouv.finances.signalements.report.dto.PointPageResponse;
import cd.gouv.finances.signalements.report.dto.PointReversalBody;
import cd.gouv.finances.signalements.report.points.CreditPointsCommand;
import cd.gouv.finances.signalements.report.points.ReadPointsQuery;
import cd.gouv.finances.signalements.report.points.ReversePointsCommand;
import cd.gouv.finances.signalements.report.prime.AssessPrimeCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Reports")
@SecurityRequirement(name = "auth")
public class PointController {

    private final ReadPointsQuery readPointsQuery;
    private final CreditPointsCommand creditPointsCommand;
    private final ReversePointsCommand reversePointsCommand;
    private final RecordAudit recordAudit;

    @GetMapping("/api/points")
    @Operation(summary = "Lists point movements without creating a reward payment")
    public PointPageResponse list(@RequestParam(defaultValue = "0") int page) {
        return readPointsQuery.execute(null, page);
    }

    @GetMapping("/api/reports/{id}/points")
    @Operation(summary = "Lists the point movements of one report")
    public PointPageResponse report(@PathVariable UUID id) {
        return readPointsQuery.execute(id, 0);
    }

    @PostMapping("/api/reports/{id}/points")
    @Operation(summary = "Credits points for one confirmed breach")
    public PointPageResponse credit(
            @PathVariable UUID id,
            @Valid @RequestBody PointCreditBody body,
            @AuthenticationPrincipal AccountUserDetails account) {
        var changes = creditPointsCommand.execute(id, body.reference(), account.getAccountId());
        recordAudit.allowed(account.getAccountId(), "points-credit", "report", id.toString());
        auditPrimes(account.getAccountId(), id, changes);
        return readPointsQuery.execute(id, 0);
    }

    @PostMapping("/api/reports/{id}/point-reversals")
    @Operation(summary = "Reverses credited points without deleting the original movement")
    public PointPageResponse reverse(
            @PathVariable UUID id,
            @Valid @RequestBody PointReversalBody body,
            @AuthenticationPrincipal AccountUserDetails account) {
        var changes = reversePointsCommand.execute(id, body.reason(), account.getAccountId());
        recordAudit.allowed(account.getAccountId(), "points-reversal", "report", id.toString());
        auditPrimes(account.getAccountId(), id, changes);
        return readPointsQuery.execute(id, 0);
    }

    private void auditPrimes(UUID actorId, UUID reportId, List<AssessPrimeCommand.Change> changes) {
        for (var change : changes) {
            if ("payout-record".equals(change.action())) {
                recordAudit.allowed(actorId, change.action(), "payout", change.id().toString());
            } else {
                recordAudit.allowed(actorId, change.action(), "report", reportId.toString());
            }
        }
    }
}
