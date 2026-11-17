package cd.gouv.finances.signalements.access.security;

import cd.gouv.finances.signalements.access.Permission;
import java.util.ArrayList;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

public class WithRoleContextFactory implements WithSecurityContextFactory<WithRole> {
    @Override public SecurityContext createSecurityContext(WithRole annotation) {
        var authorities = new ArrayList<>(Permission.forRole(annotation.value()));
        authorities.add("ROLE_" + annotation.value().name());
        var user = User.withUsername("user").password("password").authorities(authorities.toArray(String[]::new)).build();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        return context;
    }
}
