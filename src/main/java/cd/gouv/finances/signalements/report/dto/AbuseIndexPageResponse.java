package cd.gouv.finances.signalements.report.dto;

import java.util.List;

public record AbuseIndexPageResponse(
        List<AbuseIndexResponse> indices,
        List<SeriesIndexResponse> series,
        List<AccountIndexResponse> accounts,
        String retouch,
        String score,
        int page,
        int size,
        long total) {
}
