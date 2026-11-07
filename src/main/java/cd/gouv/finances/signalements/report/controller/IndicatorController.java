package cd.gouv.finances.signalements.report.controller;

import cd.gouv.finances.signalements.report.dto.IndicatorResponse;
import cd.gouv.finances.signalements.report.indicator.ReadIndicatorsQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Indicators")
@SecurityRequirement(name = "auth")
public class IndicatorController {

    private final ReadIndicatorsQuery readIndicatorsQuery;

    public IndicatorController(ReadIndicatorsQuery readIndicatorsQuery) {
        this.readIndicatorsQuery = readIndicatorsQuery;
    }

    @GetMapping("/api/indicators")
    @Operation(summary = "Returns campaign figures, the current week and the current month")
    public IndicatorResponse indicators(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String finding) {
        return readIndicatorsQuery.execute(from, to, finding);
    }
}
