package cd.gouv.finances.signalements.report.duplicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DuplicateSignals {

    private DuplicateSignals() {
    }

    public record Facts(String number, String taxId, String code) {
    }

    public record Match(UUID otherReportId, String criterion, String strength, String explanation) {
    }

    public static List<Match> compare(Facts mine, Map<UUID, Facts> others) {
        var matches = new ArrayList<Match>();
        if (mine == null || others == null) {
            return matches;
        }
        for (var other : others.entrySet()) {
            var sameNumber = same(mine.number(), other.getValue().number());
            var sameTaxId = same(mine.taxId(), other.getValue().taxId());
            var sameCode = same(mine.code(), other.getValue().code()) && text(mine.code()).length() >= 4;
            if (sameNumber && sameTaxId) {
                matches.add(new Match(
                        other.getKey(),
                        "invoice",
                        "likely",
                        "Le même numéro et le même NIF ont été lus. Le dossier n'est pas écarté tant qu'un agent n'a pas tranché."));
            } else if (sameNumber) {
                matches.add(new Match(
                        other.getKey(),
                        "number",
                        "possible",
                        "Le même numéro a été lu, sans le même NIF. Ce rapprochement reste à vérifier."));
            }
            if (sameCode) {
                matches.add(new Match(
                        other.getKey(),
                        "code",
                        "likely",
                        "Le même code a été lu sur les deux pièces. Le dossier n'est pas écarté tant qu'un agent n'a pas tranché."));
            }
        }
        return List.copyOf(matches);
    }

    private static boolean same(String left, String right) {
        var a = text(left);
        var b = text(right);
        return !a.isEmpty() && a.equals(b);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
