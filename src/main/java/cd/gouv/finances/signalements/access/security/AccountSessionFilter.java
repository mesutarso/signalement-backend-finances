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
                valid = current.isEnabled() && current.isAccountNonLocked()
                        && current.getUsername().equals(principal.getUsername())
                        && current.getPassword().equals(principal.getPassword())
                        && current instanceof AccountUserDetails currentAccount
                        && currentAccount.getAccountId().equals(principal.getAccountId())
                        && java.util.Set.copyOf(current.getAuthorities()).equals(authentication.getAuthorities().stream()
                            .filter(authority -> !(authority instanceof org.springframework.security.core.authority.FactorGrantedAuthority))
                            .collect(java.util.stream.Collectors.toSet()));
            } catch (UsernameNotFoundException exception) {
                valid = false;
            }
            if (!valid) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8);
                response.getWriter().write("{\"code\":\"session-expired\",\"message\":\"Reconnectez-vous à la plateforme.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
