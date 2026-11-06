package cd.gouv.finances.signalements.availability.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Availability")
public record AvailabilityResponse(
        @Schema(description = "True when the platform is up") boolean available) {
}
