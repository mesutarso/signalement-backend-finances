package cd.gouv.finances.signalements.report.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReportSummaryResponse(
        UUID id,
        String identifier,
        LocalDateTime reportedAt,
        String title,
        String state,
        String priority,
        String assigneeName,
        long version,
        String reporterName,
        String reporterEmail,
        String reporterPhone,
        String validationState,
        String screeningReason,
        ReportWorkResponse work) {
    public ReportSummaryResponse(UUID id,String identifier,LocalDateTime reportedAt,String title,String state,
                                 String priority,String assigneeName,long version,String reporterName,String reporterEmail,
                                 String reporterPhone,String validationState,String screeningReason) {
        this(id,identifier,reportedAt,title,state,priority,assigneeName,version,reporterName,reporterEmail,reporterPhone,
                validationState,screeningReason,null);
    }
    public ReportSummaryResponse withWork(ReportWorkResponse work) {
        return new ReportSummaryResponse(id,identifier,reportedAt,title,state,priority,assigneeName,version,reporterName,
                reporterEmail,reporterPhone,validationState,screeningReason,work);
    }
}
