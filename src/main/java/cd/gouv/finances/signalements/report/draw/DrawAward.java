package cd.gouv.finances.signalements.report.draw;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class DrawAward {

    private DrawAward() {
    }

    public record Ticket(UUID id, String personKey) {
    }

    public record Lot(String label, int quantity) {
    }

    public record Prize(UUID ticketId, String label) {
    }

    public static List<Prize> award(List<Ticket> tickets, List<Lot> lots, long seed) {
        var order = new ArrayList<>(tickets);
        Collections.shuffle(order, new Random(seed));
        var winners = new HashSet<String>();
        var prizes = new ArrayList<Prize>();
        for (var lot : lots) {
            var given = 0;
            for (var ticket : order) {
                if (given >= lot.quantity()) {
                    break;
                }
                if (!winners.add(ticket.personKey())) {
                    continue;
                }
                prizes.add(new Prize(ticket.id(), lot.label()));
                given++;
            }
        }
        return prizes;
    }
}
