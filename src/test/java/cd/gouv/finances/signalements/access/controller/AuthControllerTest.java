package cd.gouv.finances.signalements.access.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cd.gouv.finances.signalements.access.Role;
import cd.gouv.finances.signalements.access.entity.UserAccount;
import cd.gouv.finances.signalements.access.repository.UserAccountRepository;
import cd.gouv.finances.signalements.access.security.AccountDetailsService;
import cd.gouv.finances.signalements.config.ApiExceptionHandler;
import cd.gouv.finances.signalements.config.PlatformProperties;
import cd.gouv.finances.signalements.config.SecurityConfig;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AccountDetailsService.class, ApiExceptionHandler.class})
@EnableConfigurationProperties(PlatformProperties.class)
@TestPropertySource(properties = {
        "app.frontend-origin=http://localhost:3000",
        "app.documentation-open=false"
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    @Test
    void acceptsTheCookieTokenTheBrowserSends() throws Exception {
        var account = new UserAccount(
                UUID.randomUUID(),
                "admin",
                passwordEncoder.encode("Signalements-dev-2026"),
                Role.ADMINISTRATOR,
                Instant.parse("2026-11-06T00:00:00Z"));
        when(userAccountRepository.findByUsername("admin")).thenReturn(Optional.of(account));

        var issued = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent()).andReturn();
        var csrfCookie = issued.getResponse().getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/api/auth")
                        .cookie(new Cookie("XSRF-TOKEN", csrfCookie.getValue()))
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Signalements-dev-2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMINISTRATOR"));
    }

    @Test
    void opensASessionForAValidPassword() throws Exception {
        var account = new UserAccount(
                UUID.randomUUID(),
                "admin",
                passwordEncoder.encode("Signalements-dev-2026"),
                Role.ADMINISTRATOR,
                Instant.parse("2026-11-06T00:00:00Z"));
        when(userAccountRepository.findByUsername("admin")).thenReturn(Optional.of(account));

        mockMvc.perform(post("/api/auth")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Signalements-dev-2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMINISTRATOR"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void rejectsAnUnknownPasswordWithoutAHash() throws Exception {
        var account = new UserAccount(
                UUID.randomUUID(),
                "admin",
                passwordEncoder.encode("Signalements-dev-2026"),
                Role.ADMINISTRATOR,
                Instant.parse("2026-11-06T00:00:00Z"));
        when(userAccountRepository.findByUsername("admin")).thenReturn(Optional.of(account));

        mockMvc.perform(post("/api/auth")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong-password-1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("rejected-credentials"));
    }
}
