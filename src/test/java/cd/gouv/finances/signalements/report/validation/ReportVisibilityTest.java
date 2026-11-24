package cd.gouv.finances.signalements.report.validation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import cd.gouv.finances.signalements.access.Role;
import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ReportVisibilityTest {
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void unavailableDossiersAndHistoricalEndpointsRemainDeniedToAgents() {
        var jdbc = mock(JdbcTemplate.class);
        var id = UUID.randomUUID();
        var user = new AccountUserDetails(UUID.randomUUID(), "agent", "", Role.AGENT, true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        var guard = new ReportVisibility(jdbc);
        for (var suffix : new String[] {"", "/assessment", "/photos/" + UUID.randomUUID(), "/qualifications"}) {
            var request = new MockHttpServletRequest("GET", "/api/reports/" + id + suffix);
            assertThatThrownBy(() -> guard.preHandle(request, new MockHttpServletResponse(), new Object())).isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test void closedReportsAreConsultableButOnlyDoubtfulReportsCanBeChanged() {
        var jdbc = mock(JdbcTemplate.class);
        var id = UUID.randomUUID();
        var user = new AccountUserDetails(UUID.randomUUID(), "agent", "", Role.AGENT, true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        when(jdbc.queryForObject("select exists(select 1 from app.report where id = ? and validation_state <> 'legacy')", Boolean.class, id)).thenReturn(true);
        var guard = new ReportVisibility(jdbc);
        for (var suffix : new String[] {"", "/assessment", "/photos/" + UUID.randomUUID()}) {
            assertThat(guard.preHandle(new MockHttpServletRequest("GET", "/api/reports/" + id + suffix), new MockHttpServletResponse(), new Object())).isTrue();
        }
        for (var suffix : new String[] {"/assessment", "/assessment/fiscal-status", "/analysis", "/analysis/start", "/duplicates/" + UUID.randomUUID()}) {
            assertThatThrownBy(() -> guard.preHandle(new MockHttpServletRequest("POST", "/api/reports/" + id + suffix), new MockHttpServletResponse(), new Object())).isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test void agentCanPrioritizePendingAnalysisWithoutAccessToManualReprocessing() {
        var jdbc = mock(JdbcTemplate.class);
        var id = UUID.randomUUID();
        var user = new AccountUserDetails(UUID.randomUUID(), "agent", "", Role.AGENT, true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        when(jdbc.queryForObject("select exists(select 1 from app.report where id = ? and validation_state in ('queued', 'analyzing'))",
                Boolean.class, id)).thenReturn(true);
        var guard = new ReportVisibility(jdbc);
        assertThat(guard.preHandle(new MockHttpServletRequest("POST", "/api/reports/" + id + "/analysis/start"),
                new MockHttpServletResponse(), new Object())).isTrue();
        assertThatThrownBy(() -> guard.preHandle(new MockHttpServletRequest("POST", "/api/reports/" + id + "/analysis"),
                new MockHttpServletResponse(), new Object())).isInstanceOf(AccessDeniedException.class);
    }
}
