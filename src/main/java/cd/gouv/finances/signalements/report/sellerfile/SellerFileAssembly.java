package cd.gouv.finances.signalements.report.sellerfile;

import cd.gouv.finances.signalements.report.dto.SellerFileReportResponse;
import cd.gouv.finances.signalements.report.dto.SellerFileResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SellerFileAssembly {

    private SellerFileAssembly() {
    }

    public record Source(
            UUID reportId,
            String identifier,
            LocalDateTime reportedAt,
            String title,
            String decision,
            String finding,
            String proof,
            String sellerName,
            String taxId,
            String justification,
            Instant qualifiedAt) {
    }

    public static List<SellerFileResponse> identified(List<Source> sources) {
        var groups = new LinkedHashMap<String, List<Source>>();
        for (var source : sources) {
            var key = key(source.taxId());
            if (key == null) {
                continue;
            }
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(source);
        }
        return groups.values().stream()
                .map(SellerFileAssembly::identifiedFile)
                .sorted((left, right) -> right.latestAt().compareTo(left.latestAt()))
                .toList();
    }

    public static List<SellerFileResponse> unidentified(List<Source> sources) {
        return sources.stream()
                .filter(source -> key(source.taxId()) == null)
                .sorted((left, right) -> right.qualifiedAt().compareTo(left.qualifiedAt()))
                .map(source -> new SellerFileResponse(null, blankToNull(source.sellerName()), false, source.qualifiedAt(), List.of(report(source))))
                .toList();
    }

    private static SellerFileResponse identifiedFile(List<Source> sources) {
        var ordered = sources.stream().sorted((left, right) -> right.qualifiedAt().compareTo(left.qualifiedAt())).toList();
        var names = ordered.stream().map(Source::sellerName).map(SellerFileAssembly::blankToNull).filter(name -> name != null).map(name -> name.toLowerCase(Locale.ROOT)).distinct().toList();
        var displayed = names.size() == 1
                ? ordered.stream().map(Source::sellerName).map(SellerFileAssembly::blankToNull).filter(name -> name != null).findFirst().orElse(null)
                : null;
        return new SellerFileResponse(
                blankToNull(ordered.getFirst().taxId()),
                displayed,
                names.size() > 1,
                ordered.getFirst().qualifiedAt(),
                ordered.stream().map(SellerFileAssembly::report).toList());
    }

    private static SellerFileReportResponse report(Source source) {
        return new SellerFileReportResponse(
                source.reportId(),
                source.identifier(),
                source.reportedAt(),
                source.title(),
                source.decision(),
                source.finding(),
                source.proof(),
                blankToNull(source.sellerName()),
                source.justification(),
                source.qualifiedAt());
    }

    private static String key(String taxId) {
        var trimmed = blankToNull(taxId);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
