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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReadReportsQuery {

    private static final Set<String> STATES = Set.of("pending", "assigned");
    private static final Set<String> PRIORITIES = Set.of("low", "normal", "high", "urgent");

    private final ReportRepository reportRepository;
    private final AssigneeDirectory assigneeDirectory;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final tools.jackson.databind.ObjectMapper mapper;

    public record ReportSearch(String query, String state, String priority, UUID assigneeId, boolean unassigned, String validation,
                               java.time.LocalDate start, java.time.LocalDate end, String vat, String reading,
                               String missing, String duplicates, String photoRequest, String sort) {
        public ReportSearch(String query, String state, String priority, UUID assigneeId, boolean unassigned, String validation) {
            this(query,state,priority,assigneeId,unassigned,validation,null,null,null,null,null,null,null,null);
        }
        public ReportSearch(String query, String state, String priority, UUID assigneeId, boolean unassigned) {
            this(query, state, priority, assigneeId, unassigned, null);
        }
    }

    @PreAuthorize("hasAuthority('report.read')")
    @Transactional(readOnly = true)
    public ReportPageResponse execute(int page, int size, boolean includeReporter, ReportSearch search) {
        var safePage = Math.max(page, 0);
        var safeSize = Math.min(Math.max(size, 1), 100);
        if (search.start()!=null && search.end()!=null && search.end().isBefore(search.start())) throw new ReportRejected("period",400);
        var sortKey=known(search.sort(),Set.of("newest","oldest","seller","invoice","status"));
        var order=switch(sortKey==null?"newest":sortKey) {
            case "oldest" -> Sort.Order.asc("reportedAt");
            case "seller" -> Sort.Order.asc("assessment.sellerName");
            case "invoice" -> Sort.Order.asc("assessment.documentNumber");
            case "status" -> Sort.Order.asc("validationState");
            default -> Sort.Order.desc("reportedAt");
        };
        var found = reportRepository.findAll(specification(search, includeReporter), PageRequest.of(
                safePage, safeSize, Sort.by(order, Sort.Order.asc("referenceCode"),Sort.Order.asc("id"))));
        var names = assigneeDirectory.names(found.getContent().stream()
                .map(Report::getAssigneeId)
                .filter(id -> id != null)
                .collect(Collectors.toSet()));
        var ids=found.getContent().stream().map(Report::getId).toList();
        var work=new java.util.HashMap<UUID,cd.gouv.finances.signalements.report.dto.ReportWorkResponse>();
        if(!ids.isEmpty()) {
            var marks=String.join(",",ids.stream().map(id->"?").toList());
            jdbc.query("""
                    select a.*, exists(select 1 from app.photo_request p where p.report_id=a.report_id and p.status='open') as photo_requested
                    from app.report_assessment a where a.report_id in (%s)
                    """.formatted(marks),(org.springframework.jdbc.core.RowCallbackHandler) r -> {
                var extracted=mapper.readTree(r.getString("extracted"));
                work.put(r.getObject("report_id",UUID.class),new cd.gouv.finances.signalements.report.dto.ReportWorkResponse(
                        r.getString("seller_name"),r.getString("seller_tax_id"),r.getString("document_number"),r.getObject("document_date",java.time.LocalDate.class),
                        extracted.path("totalAmount").asString(null),extracted.path("currency").asString(null),r.getString("vat_status"),
                        r.getString("job_state"),r.getString("reason"),r.getBoolean("photo_requested")));
            },ids.toArray());
        }
        return new ReportPageResponse(found.getContent().stream().map(r->ReportMapper.toSummary(r,includeReporter,names).withWork(work.get(r.getId()))).toList(),
                found.getNumber(),found.getSize(),found.getTotalElements());
    }

    private static Specification<Report> specification(ReportSearch search, boolean includeReporter) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var agent = authentication != null && authentication.getAuthorities().stream().anyMatch(a -> "ROLE_AGENT".equals(a.getAuthority()));
        var requested = agent && (search.validation() == null || search.validation().isBlank()) ? "doubt" : search.validation();
        var selected = known(requested, Set.of("all", "queued", "analyzing", "doubt", "validated", "rejected", "legacy",
                "rejected-unreadable", "rejected-identity-missing", "rejected-duplicate", "completion"));
        if (agent && "legacy".equals(selected)) throw new ReportRejected("validation", 400);
        var screening = selected != null && selected.startsWith("rejected-") ? selected.substring("rejected-".length()) : null;
        var validation = screening != null ? "rejected" : "all".equals(selected) ? null : "completion".equals(selected)?"doubt":selected;
        var state = known(search.state(), STATES);
        var unsetPriority = "unset".equals(search.priority());
        var priority = unsetPriority ? null : known(search.priority(), PRIORITIES);
        var vat=known(search.vat(),Set.of("unknown","registered","unregistered"));
        var reading=known(search.reading(),Set.of("pending","done","failed","unreadable"));
        var missing=known(search.missing(),Set.of("nif","number","date","any"));
        var duplicate=known(search.duplicates(),Set.of("open","confirmed","none"));
        var photoRequest=known(search.photoRequest(),Set.of("open","linked","none"));
        return (root, query, builder) -> {
            var assessment=root.join("assessment",jakarta.persistence.criteria.JoinType.LEFT);
            var predicates = new ArrayList<Predicate>();
            if (agent) predicates.add(builder.notEqual(root.get("validationState"), "legacy"));
            if (validation != null) predicates.add(builder.equal(root.get("validationState"), validation));
            if (screening != null) predicates.add(builder.equal(root.get("screeningReason"), screening));
            if("completion".equals(selected)) predicates.add(builder.or(builder.isNull(assessment.get("sellerTaxId")),
                    builder.isNull(assessment.get("documentNumber")),builder.isNull(assessment.get("documentDate")),
                    builder.equal(root.get("screeningReason"),"unreadable")));
            if(search.start()!=null) predicates.add(builder.greaterThanOrEqualTo(root.get("reportedOn"),search.start()));
            if(search.end()!=null) predicates.add(builder.lessThanOrEqualTo(root.get("reportedOn"),search.end()));
            if(vat!=null) predicates.add(builder.equal(assessment.get("vatStatus"),vat));
            if(reading!=null) predicates.add(switch(reading) {
                case "pending" -> assessment.get("jobState").in("queued","running");
                case "failed" -> builder.isNotNull(assessment.get("errorCode"));
                case "unreadable" -> builder.equal(root.get("screeningReason"),"unreadable");
                default -> builder.and(builder.equal(assessment.get("jobState"),"done"),builder.isNull(assessment.get("errorCode")));
            });
            if(missing!=null) {
                var nif=builder.or(builder.isNull(assessment.get("sellerTaxId")),builder.equal(assessment.get("sellerTaxId"),""));
                var number=builder.or(builder.isNull(assessment.get("documentNumber")),builder.equal(assessment.get("documentNumber"),""));
                var date=builder.isNull(assessment.get("documentDate"));
                predicates.add(switch(missing) { case "nif"->nif; case "number"->number; case "date"->date; default->builder.or(nif,number,date); });
            }
            if(duplicate!=null) {
                var sub=query.subquery(UUID.class); var alert=sub.from(cd.gouv.finances.signalements.report.entity.DuplicateAlert.class);
                sub.select(alert.get("id")).where(builder.or(builder.equal(alert.get("reportId"),root.get("id")),builder.equal(alert.get("otherReportId"),root.get("id"))),
                        "none".equals(duplicate)?alert.get("status").in("open","confirmed"):builder.equal(alert.get("status"),duplicate));
                predicates.add("none".equals(duplicate)?builder.not(builder.exists(sub)):builder.exists(sub));
            }
            if(photoRequest!=null) {
                var sub=query.subquery(UUID.class); var request=sub.from(cd.gouv.finances.signalements.report.entity.PhotoRequest.class);
                sub.select(request.get("id")).where(builder.equal(request.get("reportId"),root.get("id")),
                        builder.equal(request.get("status"),"none".equals(photoRequest)?"open":photoRequest));
                predicates.add("none".equals(photoRequest)?builder.not(builder.exists(sub)):builder.exists(sub));
            }
            if (search.query() != null && !search.query().isBlank()) {
                var pattern = "%" + escape(search.query().trim().toLowerCase(Locale.ROOT)) + "%";
                var matches = new ArrayList<Predicate>();
                matches.add(contains(builder, root.get("externalId"), pattern));
                matches.add(contains(builder, root.get("title"), pattern));
                matches.add(contains(builder, root.get("description"), pattern));
                matches.add(contains(builder,assessment.get("sellerName"),pattern));
                matches.add(contains(builder,assessment.get("sellerTaxId"),pattern));
                matches.add(contains(builder,assessment.get("documentNumber"),pattern));
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
