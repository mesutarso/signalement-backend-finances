package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PointMovementResponse(
        UUID id,
        UUID reportId,
        String reportReference,
        String taxId,
        String finding,
        int basePoints,
        int bonusPoints,
        String kind,
        String dgiReference,
        String reason,
        LocalDate filedOn,
        Instant recordedAt) {
}
