package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.dto.DrawBody;
import cd.gouv.finances.signalements.report.dto.DrawPageResponse;
import cd.gouv.finances.signalements.report.dto.DrawTicketBody;
import cd.gouv.finances.signalements.report.dto.KeptResponse;
import cd.gouv.finances.signalements.report.dto.PrizeHandoverBody;
import cd.gouv.finances.signalements.report.draw.OrganizeDrawCommand;
import cd.gouv.finances.signalements.report.draw.PrepareDrawTicketsCommand;
import cd.gouv.finances.signalements.report.draw.PublishDrawCommand;
import cd.gouv.finances.signalements.report.draw.ReadDrawsQuery;
import cd.gouv.finances.signalements.report.draw.RecordPrizeHandoverCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@Tag(name = "Draws")
@SecurityRequirement(name = "auth")
public class DrawController {

    private final ReadDrawsQuery readDrawsQuery;
    private final PrepareDrawTicketsCommand prepareDrawTicketsCommand;
    private final OrganizeDrawCommand organizeDrawCommand;
    private final PublishDrawCommand publishDrawCommand;
    private final RecordPrizeHandoverCommand recordPrizeHandoverCommand;
    private final RecordAudit recordAudit;

    @GetMapping("/api/draws")
    @Operation(summary = "Lists draw tickets, draws and published results without identities")
    public DrawPageResponse list() {
        return readDrawsQuery.execute();
    }

    @PostMapping("/api/draw-tickets")
    @Operation(summary = "Keeps one monthly ticket and one final ticket for each report confirmed in the month")
    public KeptResponse tickets(@Valid @RequestBody DrawTicketBody body, @AuthenticationPrincipal AccountUserDetails account) {
        var created = prepareDrawTicketsCommand.execute(body.month());
        if (created > 0) {
            recordAudit.allowed(account.getAccountId(), "draw-tickets", "draw", body.month().toString());
        }
        return new KeptResponse(created);
    }

    @PostMapping("/api/draws")
    @Operation(summary = "Draws the lots for one month or the final draw and keeps the witness and the seed")
    public DrawPageResponse organize(@Valid @RequestBody DrawBody body, @AuthenticationPrincipal AccountUserDetails account) {
        var id = organizeDrawCommand.execute(body.kind(), body.month(), body.witness(), body.campaign(), body.intermediate(), body.motorcycles(), body.cars(), account.getAccountId());
        recordAudit.allowed(account.getAccountId(), "draw", "draw", id.toString());
        return readDrawsQuery.execute();
    }

    @PostMapping("/api/draws/{id}/publications")
    @Operation(summary = "Publishes a draw without the winner identity")
    public DrawPageResponse publish(@PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails account) {
        publishDrawCommand.execute(id, account.getAccountId());
        recordAudit.allowed(account.getAccountId(), "draw-publication", "draw", id.toString());
        return readDrawsQuery.execute();
    }

    @PostMapping("/api/draws/prizes/{id}/handover")
    @Operation(summary = "Records that a lot was handed over without publishing the winner identity")
    public DrawPageResponse handover(@PathVariable UUID id, @RequestBody PrizeHandoverBody body, @AuthenticationPrincipal AccountUserDetails account) {
        recordPrizeHandoverCommand.execute(id, body.note(), account.getAccountId());
        recordAudit.allowed(account.getAccountId(), "prize-handover", "prize", id.toString());
        return readDrawsQuery.execute();
    }
}
