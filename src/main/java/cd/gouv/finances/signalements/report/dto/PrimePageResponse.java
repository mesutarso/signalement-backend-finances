package cd.gouv.finances.signalements.report.dto;

import java.util.List;

public record PrimePageResponse(List<PrimeTierResponse> items, int page, int size, long total) {
}
