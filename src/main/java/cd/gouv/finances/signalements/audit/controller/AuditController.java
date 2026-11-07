package cd.gouv.finances.signalements.audit.controller;

import cd.gouv.finances.signalements.audit.dto.AuditPageResponse;
import cd.gouv.finances.signalements.audit.query.ReadAuditQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Audit")
@SecurityRequirement(name = "auth")
public class AuditController {

    private final ReadAuditQuery readAuditQuery;

    @GetMapping("/api/audit")
    @Operation(summary = "Lists recorded actions for an object and a period")
    public AuditPageResponse list(
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String objectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page) {
        return readAuditQuery.execute(objectType, objectId, from, to, page);
    }
}
