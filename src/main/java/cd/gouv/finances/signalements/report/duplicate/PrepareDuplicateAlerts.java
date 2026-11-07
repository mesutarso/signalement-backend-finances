package cd.gouv.finances.signalements.report.duplicate;

import cd.gouv.finances.signalements.report.entity.DuplicateAlert;
import cd.gouv.finances.signalements.report.repository.DuplicateAlertRepository;
import cd.gouv.finances.signalements.report.repository.ReportRepository;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PrepareDuplicateAlerts {

    private final ReportRepository reportRepository;
    private final DuplicateAlertRepository duplicateAlertRepository;
    private final DuplicateLookup duplicateLookup;
    private final StampPhotoFingerprints stampPhotoFingerprints;
    private final Clock clock;

    @Transactional
    public void execute(UUID reportId) {
        if (!reportRepository.existsById(reportId)) {
            return;
        }
        stampPhotoFingerprints.stampReport(reportId);
        var seen = new HashSet<String>();
        for (var pair : duplicateLookup.photoPairs(reportId)) {
            open(reportId, new DuplicateSignals.Match(
                    pair.otherReportId(),
                    DuplicateAlert.PHOTO,
                    "likely",
                    "La même image est jointe aux deux signalements. Le dossier n'est pas écarté tant qu'un agent n'a pas tranché."),
                    seen);
        }
        var facts = facts(duplicateLookup.readings());
        var mine = facts.get(reportId);
        if (mine != null) {
            facts.remove(reportId);
            for (var match : DuplicateSignals.compare(mine, facts)) {
                open(reportId, match, seen);
            }
        }
        refreshPriority(reportId);
    }

    private void open(UUID reportId, DuplicateSignals.Match match, Set<String> seen) {
        var left = reportId.compareTo(match.otherReportId()) < 0 ? reportId : match.otherReportId();
        var right = reportId.compareTo(match.otherReportId()) < 0 ? match.otherReportId() : reportId;
        if (!seen.add(left + "|" + right + "|" + match.criterion())) {
            return;
        }
        if (duplicateAlertRepository.findByReportIdAndOtherReportIdAndCriterion(left, right, match.criterion()).isPresent()) {
            return;
        }
        try {
            duplicateAlertRepository.saveAndFlush(new DuplicateAlert(
                    UUID.randomUUID(),
                    left,
                    right,
                    match.criterion(),
                    match.explanation(),
                    match.strength(),
                    clock.instant()));
        } catch (DataIntegrityViolationException ignored) {
            // Another refresh stored the same pair.
        }
    }

    private void refreshPriority(UUID reportId) {
        for (var alert : duplicateAlertRepository.findByReportIdOrOtherReportIdOrderByCreatedAtDesc(reportId, reportId)) {
            if (!DuplicateAlert.CONFIRMED.equals(alert.getStatus())) {
                continue;
            }
            var winner = winner(alert.getReportId(), alert.getOtherReportId());
            if (winner != null && !winner.id().equals(alert.getPriorityReportId())) {
                alert.confirm(alert.getReason(), alert.getActorId(), winner.id(), alert.getDecidedAt());
            }
        }
    }

    public DuplicatePriority.Side winner(UUID leftId, UUID rightId) {
        var ids = java.util.List.of(leftId, rightId);
        var timings = duplicateLookup.timings(ids);
        var complete = new HashSet<>(duplicateLookup.completeReports(ids));
        DuplicatePriority.Side left = null;
        DuplicatePriority.Side right = null;
        for (var timing : timings) {
            var side = new DuplicatePriority.Side(
                    timing.id(),
                    timing.identifier(),
                    timing.reportedAt(),
                    timing.referenceCode(),
                    complete.contains(timing.id()));
            if (timing.id().equals(leftId)) {
                left = side;
            } else if (timing.id().equals(rightId)) {
                right = side;
            }
        }
        if (left == null || right == null) {
            return null;
        }
        return DuplicatePriority.choose(left, right);
    }

    private static java.util.Map<UUID, DuplicateSignals.Facts> facts(java.util.List<DuplicateLookup.FieldRow> rows) {
        var grouped = new HashMap<UUID, HashMap<String, Set<String>>>();
        for (var row : rows) {
            if (row.value() == null || row.value().isBlank()) {
                continue;
            }
            grouped.computeIfAbsent(row.reportId(), id -> new HashMap<>())
                    .computeIfAbsent(row.name(), name -> new HashSet<>())
                    .add(row.value().trim());
        }
        var result = new HashMap<UUID, DuplicateSignals.Facts>();
        for (var entry : grouped.entrySet()) {
            result.put(entry.getKey(), new DuplicateSignals.Facts(
                    single(entry.getValue().get("documentNumber")),
                    single(entry.getValue().get("sellerTaxId")),
                    single(entry.getValue().get("readCode"))));
        }
        return result;
    }

    private static String single(Set<String> values) {
        if (values == null || values.size() != 1) {
            return null;
        }
        return values.iterator().next();
    }
}
