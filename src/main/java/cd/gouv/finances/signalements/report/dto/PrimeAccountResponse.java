package cd.gouv.finances.signalements.report.dto;

import java.util.List;

public record PrimeAccountResponse(
        int netPoints,
        int surplus,
        int grantedAmount,
        int ceiling,
        String phoneState,
        String phone,
        List<PrimeTierResponse> tiers) {
}
