package cd.gouv.finances.signalements.report.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DrawPageResponse(List<Ticket> tickets, List<Draw> draws, List<Result> results, List<Prize> prizes) {

    public record Ticket(UUID id, String reportReference, String kind, LocalDate periodStart) {
    }

    public record Draw(UUID id, String reference, String kind, LocalDate periodStart, String witness, String status) {
    }

    public record Result(UUID id, String reference, String label, String reportReference) {
    }

    public record Prize(UUID id, String reference, String label, String reportReference, boolean handed) {
    }
}
