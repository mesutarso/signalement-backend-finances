package cd.gouv.finances.signalements.report.dto;

import java.util.List;

public record SellerFilePageResponse(List<SellerFileResponse> items, int page, int size, long total) {
}
