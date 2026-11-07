package cd.gouv.finances.signalements.report.dto;

import java.time.Instant;
import java.time.LocalDate;

public record IndicatorResponse(
        LocalDate from,
        LocalDate to,
        String finding,
        Figures figures,
        Figures week,
        Figures month,
        Instant asOf) {

    public record Figures(
            LocalDate from,
            LocalDate to,
            long received,
            long treated,
            long findings,
            long transmissions,
            long responses,
            long points,
            long primes,
            long paidAmount,
            long payments,
            long draws,
            long alerts) {
    }
}
