package cd.gouv.finances.signalements.report.query;

import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.assignment.AssigneeDirectory;
import cd.gouv.finances.signalements.report.dto.ReportPageResponse;
import cd.gouv.finances.signalements.report.entity.Report;
import cd.gouv.finances.signalements.report.mapper.ReportMapper;
import cd.gouv.finances.signalements.report.repository.ReportRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReadReportsQuery {

    private static final Set<String> STATES = Set.of("pending", "assigned");
    private static final Set<String> PRIORITIES = Set.of("low", "normal", "high", "urgent");

    private final ReportRepository reportRepository;
    private final AssigneeDirectory assigneeDirectory;

    public record ReportSearch(String query, String state, String priority, UUID assigneeId, boolean unassigned) {
    }

    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','AUDITOR','ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public ReportPageResponse execute(int page, int size, boolean includeReporter, ReportSearch search) {
        var safePage = Math.max(page, 0);
        var safeSize = Math.min(Math.max(size, 1), 100);
        var found = reportRepository.findAll(specification(search, includeReporter), PageRequest.of(
                safePage, safeSize, Sort.by(Sort.Order.desc("reportedAt"), Sort.Order.asc("referenceCode"))));
        var names = assigneeDirectory.names(found.getContent().stream()
                .map(Report::getAssigneeId)
                .filter(id -> id != null)
                .collect(Collectors.toSet()));
        return ReportMapper.toPage(found, includeReporter, names);
    }

    private static Specification<Report> specification(ReportSearch search, boolean includeReporter) {
        var state = known(search.state(), STATES);
        var unsetPriority = "unset".equals(search.priority());
        var priority = unsetPriority ? null : known(search.priority(), PRIORITIES);
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (search.query() != null && !search.query().isBlank()) {
                var pattern = "%" + escape(search.query().trim().toLowerCase(Locale.ROOT)) + "%";
                var matches = new ArrayList<Predicate>();
                matches.add(contains(builder, root.get("externalId"), pattern));
                matches.add(contains(builder, root.get("title"), pattern));
                matches.add(contains(builder, root.get("description"), pattern));
                if (includeReporter) {
                    matches.add(contains(builder, root.get("reporterName"), pattern));
                    matches.add(contains(builder, root.get("reporterEmail"), pattern));
                    matches.add(contains(builder, root.get("reporterPhone"), pattern));
                }
                predicates.add(builder.or(matches.toArray(Predicate[]::new)));
            }
            if (state != null) {
                predicates.add(builder.equal(root.get("state"), state));
            }
            if (unsetPriority) {
                predicates.add(builder.isNull(root.get("priority")));
            } else if (priority != null) {
                predicates.add(builder.equal(root.get("priority"), priority));
            }
            if (search.unassigned()) {
                predicates.add(builder.isNull(root.get("assigneeId")));
            } else if (search.assigneeId() != null) {
                predicates.add(builder.equal(root.get("assigneeId"), search.assigneeId()));
            }
            return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String known(String value, Set<String> allowed) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!allowed.contains(value)) {
            throw new ReportRejected("validation", 400);
        }
        return value;
    }

    private static Predicate contains(
            jakarta.persistence.criteria.CriteriaBuilder builder,
            jakarta.persistence.criteria.Expression<String> field,
            String pattern) {
        return builder.like(builder.lower(builder.coalesce(field, "")), pattern, '\\');
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
