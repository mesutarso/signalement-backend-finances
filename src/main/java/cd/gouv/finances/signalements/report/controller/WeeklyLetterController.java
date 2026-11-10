package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.report.letter.WeeklyLettersCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class WeeklyLetterController {
    private final WeeklyLettersCommand command;
    public record Routing(@NotBlank @Size(max = 40) String taxId, @NotBlank @Size(max = 160) String service) { }
    public record Dispatch(@NotNull LocalDate sentOn, @NotBlank @Size(max = 160) String reference) { }

    @GetMapping("/api/letter-routes")
    public List<WeeklyLettersCommand.Route> routes() { return command.routes(); }

    @PostMapping("/api/letter-routes")
    public List<WeeklyLettersCommand.Route> route(@Valid @RequestBody Routing body, @AuthenticationPrincipal AccountUserDetails actor) {
        command.route(body.taxId(), body.service(), actor.getAccountId());
        return command.routes();
    }

    @PostMapping("/api/letters/weekly")
    public List<UUID> prepare(@AuthenticationPrincipal AccountUserDetails actor) { return command.prepare(actor.getAccountId()); }

    @PostMapping("/api/letters/{id}/dispatches")
    public void dispatch(@PathVariable UUID id, @Valid @RequestBody Dispatch body, @AuthenticationPrincipal AccountUserDetails actor) {
        command.dispatch(id, body.sentOn(), body.reference(), actor.getAccountId());
    }

    @GetMapping("/api/letters/{id}/dispatches")
    public WeeklyLettersCommand.DispatchInfo dispatchInfo(@PathVariable UUID id) { return command.dispatchInfo(id); }
}
