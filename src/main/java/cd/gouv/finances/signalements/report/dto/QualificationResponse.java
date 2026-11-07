package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record QualificationResponse(
        UUID id,
        String decision,
        String finding,
        String proof,
        String justification,
        String sellerName,
        String sellerTaxId,
        LocalDate issuedOn,
        String amount,
        String documentNumber,
        String code,
        String missingElements,
        String dgiReference,
        String actorName,
        Instant recordedAt) {
}
