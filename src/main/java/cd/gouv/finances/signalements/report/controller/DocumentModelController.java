package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.dto.DocumentModelBody;
import cd.gouv.finances.signalements.report.dto.DocumentModelResponse;
import cd.gouv.finances.signalements.report.message.ReadDocumentModelsQuery;
import cd.gouv.finances.signalements.report.message.RecordDocumentModelCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Document models")
@SecurityRequirement(name = "auth")
public class DocumentModelController {

    private final ReadDocumentModelsQuery readDocumentModelsQuery;
    private final RecordDocumentModelCommand recordDocumentModelCommand;
    private final RecordAudit recordAudit;

    @GetMapping("/api/document-models")
    @Operation(summary = "Lists the active message models")
    public DocumentModelResponse list() {
        return readDocumentModelsQuery.execute();
    }

    @PostMapping("/api/document-models")
    @Operation(summary = "Records a message model for later messages")
    public DocumentModelResponse record(@Valid @RequestBody DocumentModelBody body, @AuthenticationPrincipal AccountUserDetails account) {
        var id = recordDocumentModelCommand.execute(body.kind(), body.body(), account.getAccountId());
        recordAudit.allowed(account.getAccountId(), "message-model", "message", id.toString());
        return readDocumentModelsQuery.execute();
    }
}
