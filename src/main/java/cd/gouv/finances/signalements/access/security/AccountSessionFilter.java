package cd.gouv.finances.signalements.access.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects an existing session after its account loses access or changes role. */
public class AccountSessionFilter extends OncePerRequestFilter {

    private final UserDetailsService userDetailsService;

    public AccountSessionFilter(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AccountUserDetails principal) {
            boolean valid;
            try {
                var current = userDetailsService.loadUserByUsername(principal.getUsername());
                valid = current.isEnabled() && current.getAuthorities().equals(principal.getAuthorities());
            } catch (UsernameNotFoundException exception) {
                valid = false;
            }
            if (!valid) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"session-expired\",\"message\":\"Reconnectez-vous à la plateforme.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
