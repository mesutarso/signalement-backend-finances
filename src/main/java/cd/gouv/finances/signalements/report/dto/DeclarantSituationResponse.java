package cd.gouv.finances.signalements.report.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DeclarantSituationResponse(
        String reference,
        int rewardAmount,
        long validatedCount,
        long validatedRewardAmount,
        int paidAmount,
        int ceiling,
        int remainder,
        int tickets,
        int lots,
        boolean rewardApproved,
        boolean rewardPaid,
        boolean identified,
        Identity identity,
        long rejectedCount,
        long duplicateCount,
        long pendingCount,
        long reportsTotal,
        int reportsPage,
        int reportsSize,
        List<Report> reports) {

    public record Identity(String name, String email, String phone) { }

    public record Report(UUID id, String reference, String status, String title, LocalDateTime reportedAt) {
    }
}
