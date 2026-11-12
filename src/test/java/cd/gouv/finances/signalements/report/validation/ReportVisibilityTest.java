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

    @Test void aDirectPhotoLinkCannotExposeAClosedDossierToAnAgent() {
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
}
