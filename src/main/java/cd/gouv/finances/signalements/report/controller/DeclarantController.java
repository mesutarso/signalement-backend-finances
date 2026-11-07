package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.report.declarant.ReadDeclarantSituationQuery;
import cd.gouv.finances.signalements.report.dto.DeclarantSituationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Declarants")
@SecurityRequirement(name = "auth")
public class DeclarantController {

    private final ReadDeclarantSituationQuery readDeclarantSituationQuery;

    public DeclarantController(ReadDeclarantSituationQuery readDeclarantSituationQuery) {
        this.readDeclarantSituationQuery = readDeclarantSituationQuery;
    }

    @GetMapping("/api/declarants")
    @Operation(summary = "Returns one declarant situation from a report reference, without seller tax data")
    public DeclarantSituationResponse one(@RequestParam String reference) {
        return readDeclarantSituationQuery.execute(reference);
    }
}
