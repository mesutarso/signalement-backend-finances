package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.dto.DeclarantMessageBody;
import cd.gouv.finances.signalements.report.dto.DeclarantMessagePageResponse;
import cd.gouv.finances.signalements.report.message.PrepareDeclarantMessageCommand;
import cd.gouv.finances.signalements.report.message.ReadDeclarantMessagesQuery;
import cd.gouv.finances.signalements.report.message.SendDeclarantMessageCommand;
import cd.gouv.finances.signalements.sms.SmsResult;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Declarant messages")
@SecurityRequirement(name = "auth")
public class DeclarantMessageController {

    private final ReadDeclarantMessagesQuery readDeclarantMessagesQuery;
    private final PrepareDeclarantMessageCommand prepareDeclarantMessageCommand;
    private final RecordAudit recordAudit;
    private final SendDeclarantMessageCommand sendDeclarantMessageCommand;

    @PostMapping("/api/declarant-messages/{id}/deliveries")
    public SmsResult send(@PathVariable UUID id, @AuthenticationPrincipal AccountUserDetails account) {
        return sendDeclarantMessageCommand.execute(id, account.getAccountId());
    }

    @GetMapping("/api/declarant-messages")
    @Operation(summary = "Lists prepared declarant messages that have not been sent")
    public DeclarantMessagePageResponse list(@RequestParam(defaultValue = "0") int page) {
        return readDeclarantMessagesQuery.execute(page);
    }

    @PostMapping("/api/declarant-messages")
    @Operation(summary = "Prepares one declarant message from the report reference")
    public DeclarantMessagePageResponse prepare(@Valid @RequestBody DeclarantMessageBody body, @AuthenticationPrincipal AccountUserDetails account) {
        var id = prepareDeclarantMessageCommand.execute(body.reference(), body.kind(), account.getAccountId());
        recordAudit.allowed(account.getAccountId(), "declarant-message", "message", id.toString());
        return readDeclarantMessagesQuery.execute(0);
    }
}
