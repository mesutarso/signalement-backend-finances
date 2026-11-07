package cd.gouv.finances.signalements.report.dto;

import java.time.LocalDate;
import java.util.UUID;

public record LetterLineResponse(
        UUID id,
        String annex,
        UUID reportId,
        String reportReference,
        String taxId,
        String sellerName,
        String decision,
        String finding,
        String proof,
        String documentNumber,
        LocalDate issuedOn,
        String amount,
        String justification,
        String missingElements,
        String dgiReference,
        UUID photoId,
        String contentType) {
}
