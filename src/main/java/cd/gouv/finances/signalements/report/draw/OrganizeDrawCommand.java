package cd.gouv.finances.signalements.report.draw;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.entity.CampaignDraw;
import cd.gouv.finances.signalements.report.entity.DrawPrize;
import cd.gouv.finances.signalements.report.entity.DrawTicket;
import cd.gouv.finances.signalements.report.repository.CampaignDrawRepository;
import cd.gouv.finances.signalements.report.repository.DrawPrizeRepository;
import cd.gouv.finances.signalements.report.repository.DrawTicketRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OrganizeDrawCommand {

    private static final ZoneId KINSHASA = ZoneId.of("Africa/Kinshasa");

    private final JdbcTemplate jdbc;
    private final CampaignDrawRepository campaignDrawRepository;
    private final DrawPrizeRepository drawPrizeRepository;
    private final DrawTicketRepository drawTicketRepository;
    private final Clock clock;

    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRATOR')")
    @Transactional
    public UUID execute(String kind, LocalDate month, String witness, int campaign, int intermediate, int motorcycles, int cars, UUID actorId) {
        jdbc.queryForObject("select pg_advisory_xact_lock(783962143)", Object.class);
        var drawKind = DrawTicket.FINAL.equals(kind) ? DrawTicket.FINAL : DrawTicket.MONTHLY.equals(kind) ? DrawTicket.MONTHLY : "";
        if (drawKind.isEmpty()) {
            throw new ReportRejected("draw-kind", 400);
        }
        var period = DrawTicket.FINAL.equals(drawKind) ? LocalDate.of(2027, 4, 1) : month;
        if (period == null || period.getDayOfMonth() != 1) {
            throw new ReportRejected("draw-month", 400);
        }
        if (period.isBefore(LocalDate.of(2026, 11, 1)) || period.isAfter(LocalDate.of(2027, 4, 1))
                || (DrawTicket.FINAL.equals(drawKind) && LocalDate.now(clock.withZone(KINSHASA)).isBefore(period))) {
            throw new ReportRejected("draw-month", 400);
        }
        var keptWitness = witness == null ? "" : witness.trim();
        if (keptWitness.isEmpty() || keptWitness.length() > 160) {
            throw new ReportRejected("draw-witness", 400);
        }
        var lots = lots(drawKind, campaign, intermediate, motorcycles, cars);
        if (lots.isEmpty()) {
            throw new ReportRejected("draw-lots", 400);
        }
        if (DrawTicket.FINAL.equals(drawKind) ? campaignDrawRepository.existsByKind(DrawTicket.FINAL) : campaignDrawRepository.findByKindAndPeriodStart(drawKind, period).isPresent()) {
            throw new ReportRejected("draw-open", 409);
        }
        var tickets = jdbc.query("""
                select ticket.id, account.beneficiary_key, ticket.report_reference
                from app.draw_ticket ticket
                join app.report report on report.id = ticket.report_id
                left join app.beneficiary_account account on account.declarant_id = btrim(report.reporter_user_id)
                where ticket.kind = ? and ticket.period_start = ? and ticket.excluded_at is null
                order by ticket.report_reference, ticket.id
                """, (row, number) -> new Holder(row.getObject(1, UUID.class), row.getString(2), row.getString(3)), drawKind, period);
        if (tickets.isEmpty()) {
            throw new ReportRejected("draw-tickets", 404);
        }
        if (tickets.stream().anyMatch(ticket -> ticket.personKey() == null)) throw new ReportRejected("draw-identity", 409);
        var year = LocalDate.now(clock.withZone(KINSHASA)).getYear();
        var draw = new CampaignDraw(
                UUID.randomUUID(),
                "TR-" + year + "-" + "%06d".formatted(drawTicketRepository.nextNumber()),
                drawKind,
                period,
                keptWitness,
                ThreadLocalRandom.current().nextLong(),
                actorId,
                clock.instant());
        var prizes = DrawAward.award(
                tickets.stream().map(ticket -> new DrawAward.Ticket(ticket.id(), ticket.personKey())).toList(),
                lots,
                draw.getSeed());
        campaignDrawRepository.save(draw);
        campaignDrawRepository.flush();
        for (var index = 0; index < tickets.size(); index++) {
            var ticket = tickets.get(index);
            jdbc.update("insert into app.draw_entry (draw_id, ticket_id, position, person_key) values (?, ?, ?, ?)",
                    draw.getId(), ticket.id(), index, ticket.personKey());
        }
        for (var prize : prizes) {
            var reference = tickets.stream().filter(ticket -> ticket.id().equals(prize.ticketId())).map(Holder::reference).findFirst().orElse("");
            drawPrizeRepository.save(new DrawPrize(UUID.randomUUID(), draw.getId(), prize.ticketId(), reference, prize.label()));
        }
        return draw.getId();
    }

    private static List<DrawAward.Lot> lots(String kind, int campaign, int intermediate, int motorcycles, int cars) {
        var lots = new ArrayList<DrawAward.Lot>();
        if (DrawTicket.MONTHLY.equals(kind)) {
            add(lots, "Articles de campagne", campaign);
            add(lots, "Lot intermédiaire", intermediate);
            add(lots, "Moto", motorcycles);
            return lots;
        }
        add(lots, "Voiture", cars);
        add(lots, "Moto", motorcycles);
        return lots;
    }

    private static void add(List<DrawAward.Lot> lots, String label, int quantity) {
        if (quantity > 0) {
            lots.add(new DrawAward.Lot(label, quantity));
        }
    }

    private record Holder(UUID id, String personKey, String reference) {
    }
}
