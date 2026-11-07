package cd.gouv.finances.signalements.audit.dto;

import java.util.List;

public record AuditPageResponse(
        List<AuditEntryResponse> entries,
        int page,
        int size,
        long total) {
}
