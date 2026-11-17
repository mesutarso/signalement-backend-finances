package cd.gouv.finances.signalements.access.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import cd.gouv.finances.signalements.access.Role;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

class AccountSessionFilterTest {
    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void invalidatesADisabledAccountsExistingSession() throws Exception {
        checkRejected(Role.AGENT, false);
    }

    @Test
    void invalidatesASessionWithOutdatedPrivileges() throws Exception {
        checkRejected(Role.AUDITOR, true);
    }

    @Test
    void passwordAuthenticationFactorsDoNotInvalidateAnUnchangedAccountsSession() throws Exception {
        var principal = new AccountUserDetails(UUID.randomUUID(), "agent", "hash", Role.AGENT, true, true);
        var authorities = new java.util.ArrayList<org.springframework.security.core.GrantedAuthority>(principal.getAuthorities());
        authorities.add(org.springframework.security.core.authority.FactorGrantedAuthority.fromAuthority("FACTOR_PASSWORD"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
        var service = mock(UserDetailsService.class);
        when(service.loadUserByUsername("agent")).thenReturn(principal);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        new AccountSessionFilter(service).doFilter(new MockHttpServletRequest(), response, chain);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void invalidatesASessionAfterAPasswordReset() throws Exception {
        var id = UUID.randomUUID();
        var old = new AccountUserDetails(id, "agent", "old-hash", Role.AGENT, true, true);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(old, null, old.getAuthorities()));
        SecurityContextHolder.setContext(context);
        var service = mock(UserDetailsService.class);
        when(service.loadUserByUsername("agent")).thenReturn(new AccountUserDetails(id, "agent", "new-hash", Role.AGENT, true, true));
        var response = new MockHttpServletResponse();
        new AccountSessionFilter(service).doFilter(new MockHttpServletRequest(), response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void invalidatesLegacyRoleOnlySessionsInsteadOfKeepingStaleAuthorities() throws Exception {
        var id = UUID.randomUUID();
        var principal = new AccountUserDetails(id, "agent", "hash", Role.AGENT, true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_AGENT"))));
        var service = mock(UserDetailsService.class);
        when(service.loadUserByUsername("agent")).thenReturn(principal);
        var response = new MockHttpServletResponse();
        new AccountSessionFilter(service).doFilter(new MockHttpServletRequest(), response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(401);
    }

    private void checkRejected(Role nextRole, boolean enabled) throws Exception {
        var id = UUID.randomUUID();
        var principal = new AccountUserDetails(id, "agent", "hash", Role.AGENT, true, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        var service = mock(UserDetailsService.class);
        when(service.loadUserByUsername("agent")).thenReturn(
                new AccountUserDetails(id, "agent", "hash", nextRole, enabled, true));
        var request = new MockHttpServletRequest();
        request.getSession();
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        new AccountSessionFilter(service).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(request.getSession(false)).isNull();
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
