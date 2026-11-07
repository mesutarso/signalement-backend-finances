package cd.gouv.finances.signalements.report.draw;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DrawAwardTest {

    @Test
    void givesOneLotPerPersonAndRepeatsTheSameSeed() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var third = UUID.randomUUID();
        var tickets = List.of(
                new DrawAward.Ticket(first, "a"),
                new DrawAward.Ticket(second, "a"),
                new DrawAward.Ticket(third, "b"));
        var lots = List.of(new DrawAward.Lot("Moto", 1), new DrawAward.Lot("Articles de campagne", 2));

        var once = DrawAward.award(tickets, lots, 7);
        var twice = DrawAward.award(tickets, lots, 7);

        assertThat(once).isEqualTo(twice);
        assertThat(once).hasSize(2);
        assertThat(once.stream().map(DrawAward.Prize::ticketId).distinct()).hasSize(2);
    }
}
