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
            var visible = jdbc.queryForObject("select exists(select 1 from app.report where id = ? and validation_state = 'doubt')",
                    Boolean.class, UUID.fromString(match.group(1)));
            if (!Boolean.TRUE.equals(visible)) throw new AccessDeniedException("Only doubtful dossiers are available to agents");
        }
        return true;
    }
}
