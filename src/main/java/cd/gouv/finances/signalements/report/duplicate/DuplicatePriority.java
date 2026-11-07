package cd.gouv.finances.signalements.report.duplicate;

import java.time.LocalDateTime;
import java.util.UUID;

public final class DuplicatePriority {

    private DuplicatePriority() {
    }

    public record Side(
            UUID id,
            String identifier,
            LocalDateTime reportedAt,
            String referenceCode,
            boolean complete) {
    }

    public static Side choose(Side left, Side right) {
        if (left.complete() != right.complete()) {
            return left.complete() ? left : right;
        }
        var byDate = left.reportedAt().compareTo(right.reportedAt());
        if (byDate != 0) {
            return byDate < 0 ? left : right;
        }
        var byReference = left.referenceCode().compareTo(right.referenceCode());
        if (byReference != 0) {
            return byReference < 0 ? left : right;
        }
        return left.id().compareTo(right.id()) <= 0 ? left : right;
    }

    public static String basis(Side winner, Side other) {
        if (winner.complete() || other.complete()) {
            return "complete";
        }
        if (!winner.reportedAt().equals(other.reportedAt())) {
            return "received";
        }
        return "reference";
    }
}
