package cd.gouv.finances.signalements.availability.controller;

import cd.gouv.finances.signalements.availability.dto.AvailabilityResponse;
import cd.gouv.finances.signalements.availability.query.ReadAvailabilityQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Availability")
public class AvailabilityController {

    private final ReadAvailabilityQuery readAvailabilityQuery;

    @GetMapping("/api/availability")
    @Operation(summary = "Reports whether the platform is up")
    public AvailabilityResponse read() {
        return readAvailabilityQuery.execute();
    }
}
