package cd.gouv.finances.signalements.report.validation;

import cd.gouv.finances.signalements.access.Role;
import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** The same access restriction covers the dossier, attachments and all nested commands. */
@Component
@RequiredArgsConstructor
public class ReportVisibility implements HandlerInterceptor {
    private static final Pattern DOSSIER = Pattern.compile("^/api/reports/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(?:/.*)?$");
    private final JdbcTemplate jdbc;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountUserDetails account)
                || account.getRole() != Role.AGENT) return true;
        var match = DOSSIER.matcher(request.getRequestURI());
        if (match.matches()) {
            if (request.getRequestURI().endsWith("/points") || request.getRequestURI().endsWith("/prime")
                    || request.getRequestURI().endsWith("/qualifications")) throw new AccessDeniedException("Historical actions are reserved for oversight");
            var readOnly = "GET".equals(request.getMethod()) && (request.getRequestURI().equals("/api/reports/" + match.group(1))
                    || request.getRequestURI().endsWith("/assessment")
                    || request.getRequestURI().matches(".*/photos/[0-9a-fA-F-]{36}$"));
            var startOnOpen = "POST".equals(request.getMethod())
                    && request.getRequestURI().equals("/api/reports/" + match.group(1) + "/analysis/start");
            var availableStates = readOnly ? "validation_state <> 'legacy'"
                    : startOnOpen ? "validation_state in ('queued', 'analyzing')" : "validation_state = 'doubt'";
            var visible = jdbc.queryForObject("select exists(select 1 from app.report where id = ? and "
                            + availableStates + ")",
                    Boolean.class, UUID.fromString(match.group(1)));
            if (!Boolean.TRUE.equals(visible)) throw new AccessDeniedException("This dossier is not available for this action");
        }
        return true;
    }
}
