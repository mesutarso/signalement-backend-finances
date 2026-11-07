package cd.gouv.finances.signalements.report.prime;

import java.util.ArrayList;
import java.util.List;

public final class PrimeScale {

    public static final int CEILING = 1_000_000;

    private PrimeScale() {
    }

    public record Tier(int threshold, int amount) {
    }

    public static List<Tier> reached(int netPoints) {
        return reached(netPoints, CEILING, cdcTiers());
    }

    public static List<Tier> reached(int netPoints, int ceiling, List<Tier> schedule) {
        if (netPoints <= 0) {
            return List.of();
        }
        var reached = new ArrayList<Tier>();
        var granted = 0;
        for (var tier : schedule) {
            if (netPoints < tier.threshold() || granted + tier.amount() > ceiling) {
                break;
            }
            reached.add(tier);
            granted += tier.amount();
        }
        return List.copyOf(reached);
    }

    public static int surplus(int netPoints) {
        return surplus(netPoints, CEILING, cdcTiers());
    }

    public static int surplus(int netPoints, int ceiling, List<Tier> schedule) {
        var reached = reached(netPoints, ceiling, schedule);
        var covered = reached.isEmpty() ? 0 : reached.get(reached.size() - 1).threshold();
        return Math.max(netPoints - covered, 0);
    }

    public static int granted(int netPoints) {
        return reached(netPoints).stream().mapToInt(Tier::amount).sum();
    }

    public static List<Tier> cdcTiers() {
        return schedule();
    }

    private static List<Tier> schedule() {
        var tiers = new ArrayList<Tier>();
        tiers.add(new Tier(30, 10_000));
        tiers.add(new Tier(60, 20_000));
        tiers.add(new Tier(100, 30_000));
        var granted = 60_000;
        var threshold = 100;
        while (granted + 30_000 <= CEILING) {
            threshold += 50;
            tiers.add(new Tier(threshold, 30_000));
            granted += 30_000;
        }
        return List.copyOf(tiers);
    }
}
