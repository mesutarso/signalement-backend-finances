package cd.gouv.finances.signalements.report.dto;

import java.util.List;
import java.util.UUID;

public record DeclarantSituationResponse(
        String reference,
        int rewardAmount,
        int paidAmount,
        int ceiling,
        int remainder,
        int tickets,
        int lots,
        boolean rewardApproved,
        boolean rewardPaid,
        List<Report> reports) {

    public record Report(UUID id, String reference, String status) {
    }
}
