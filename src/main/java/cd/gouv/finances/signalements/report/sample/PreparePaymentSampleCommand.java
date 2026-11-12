package cd.gouv.finances.signalements.report.sample;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.PaymentSample;
import cd.gouv.finances.signalements.report.entity.PaymentSampleLine;
import cd.gouv.finances.signalements.report.repository.PaymentSampleLineRepository;
import cd.gouv.finances.signalements.report.repository.PaymentSampleRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PreparePaymentSampleCommand {

    private static final ZoneId KINSHASA = ZoneId.of("Africa/Kinshasa");

    private final JdbcTemplate jdbc;
    private final PaymentSampleRepository paymentSampleRepository;
    private final PaymentSampleLineRepository paymentSampleLineRepository;
    private final Clock clock;

    public record Paid(UUID attemptId, String reference, String operator, int amount) {
    }

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public UUID execute(LocalDate periodStart, LocalDate periodEnd, UUID actorId) {
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            throw new ReportRejected("payment-sample-period", 400);
        }
        if (paymentSampleRepository.findByPeriodStartAndPeriodEnd(periodStart, periodEnd).isPresent()) {
            throw new ReportRejected("payment-sample-open", 409);
        }
        var from = periodStart.atStartOfDay(KINSHASA).toInstant();
        var to = periodEnd.plusDays(1).atStartOfDay(KINSHASA).toInstant();
        var paid = jdbc.query("""
                select attempt.id, attempt.reference_code, attempt.operator, line.amount
                from app.payment_attempt attempt
                join app.payment_statement_line line on line.id = attempt.line_id
                where attempt.result = 'paid' and attempt.reference_code not like 'SIM-%' and attempt.recorded_at >= ? and attempt.recorded_at < ?
                order by attempt.recorded_at, attempt.id
                """, (row, number) -> new Paid(row.getObject(1, UUID.class), row.getString(2), row.getString(3), row.getInt(4)), Timestamp.from(from), Timestamp.from(to));
        if (paid.isEmpty()) {
            throw new ReportRejected("payment-sample-empty", 404);
        }
        var year = LocalDate.now(clock.withZone(KINSHASA)).getYear();
        var sample = new PaymentSample(
                UUID.randomUUID(),
                "EC-" + year + "-" + "%06d".formatted(paymentSampleRepository.nextNumber()),
                periodStart,
                periodEnd,
                actorId,
                clock.instant());
        paymentSampleRepository.save(sample);
        for (var index : PaymentSampleSelection.indexes(paid.size())) {
            var item = paid.get(index);
            paymentSampleLineRepository.save(new PaymentSampleLine(
                    UUID.randomUUID(), sample.getId(), item.attemptId(), item.reference(), item.operator(), item.amount()));
        }
        return sample.getId();
    }
}
