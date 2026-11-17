package cd.gouv.finances.signalements.access.controller;

import cd.gouv.finances.signalements.access.command.CreateAccountCommand;
import cd.gouv.finances.signalements.access.command.ReplacePasswordCommand;
import cd.gouv.finances.signalements.access.command.UpdateAccountCommand;
import cd.gouv.finances.signalements.access.dto.AccountPageResponse;
import cd.gouv.finances.signalements.access.dto.AccountResponse;
import cd.gouv.finances.signalements.access.dto.CreateAccountRequest;
import cd.gouv.finances.signalements.access.dto.ReplacePasswordRequest;
import cd.gouv.finances.signalements.access.dto.UpdateAccountRequest;
import cd.gouv.finances.signalements.access.query.ReadAccountsQuery;
import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import cd.gouv.finances.signalements.access.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Accounts")
@SecurityRequirement(name = "auth")
public class AccountController {

    private final CreateAccountCommand createAccountCommand;
    private final UpdateAccountCommand updateAccountCommand;
    private final ReplacePasswordCommand replacePasswordCommand;
    private final ReadAccountsQuery readAccountsQuery;
    private final cd.gouv.finances.signalements.access.command.ManageAccountsCommand manageAccounts;
    private final cd.gouv.finances.signalements.audit.command.RecordAudit audit;

    @GetMapping("/api/accounts")
    @Operation(summary = "Lists accounts")
    public AccountPageResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean enabled) {
        return readAccountsQuery.execute(page, size, search, role, enabled);
    }

    @PostMapping("/api/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creates an account")
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request, @AuthenticationPrincipal AccountUserDetails caller) {
        var result = createAccountCommand.execute(request);
        audit.allowed(caller.getAccountId(), "account-create", "account", result.id().toString());
        return result;
    }

    @PatchMapping("/api/accounts/{id}")
    @Operation(summary = "Changes an account role or enabled state")
    public AccountResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateAccountRequest request, @AuthenticationPrincipal AccountUserDetails caller) {
        var result = updateAccountCommand.execute(id, request);
        audit.allowed(caller.getAccountId(), "account-update", "account", id.toString());
        return result;
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/api/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails caller) {
        manageAccounts.delete(id, caller.getAccountId());
        audit.allowed(caller.getAccountId(), "account-delete", "account", id.toString());
    }

    @PostMapping("/api/accounts/{id}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlock(@PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails caller) {
        manageAccounts.unlock(id);
        audit.allowed(caller.getAccountId(), "account-unlock", "account", id.toString());
    }

    @PutMapping("/api/accounts/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Replaces an account password")
    public void replacePassword(
            @PathVariable UUID id,
            @Valid @RequestBody ReplacePasswordRequest request,
            @AuthenticationPrincipal AccountUserDetails caller) {
        replacePasswordCommand.execute(id, request, caller);
    }
}
