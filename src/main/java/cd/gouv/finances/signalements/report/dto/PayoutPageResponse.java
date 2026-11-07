package cd.gouv.finances.signalements.report.dto;

import java.util.List;

public record PayoutPageResponse(List<PayoutNumberResponse> items, int page, int size, long total) {
}
