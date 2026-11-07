package cd.gouv.finances.signalements.report.dto;

import java.util.List;
import java.util.UUID;

public record DeclarantSituationResponse(
        String reference,
        int points,
        int primeAmount,
        int paidAmount,
        int ceiling,
        int remainder,
        int tickets,
        int lots,
        boolean primeApproved,
        boolean primePaid,
        List<Report> reports) {

    public record Report(UUID id, String reference, String status, int points) {
    }
}
