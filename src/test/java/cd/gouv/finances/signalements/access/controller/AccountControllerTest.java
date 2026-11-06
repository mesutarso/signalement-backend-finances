package cd.gouv.finances.signalements.access.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cd.gouv.finances.signalements.access.command.CreateAccountCommand;
import cd.gouv.finances.signalements.access.command.ReplacePasswordCommand;
import cd.gouv.finances.signalements.access.command.UpdateAccountCommand;
import cd.gouv.finances.signalements.access.query.ReadAccountsQuery;
import cd.gouv.finances.signalements.config.PlatformProperties;
import cd.gouv.finances.signalements.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(PlatformProperties.class)
@TestPropertySource(properties = {
        "app.frontend-origin=http://localhost:3000",
        "app.documentation-open=false"
})
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private CreateAccountCommand createAccountCommand;

    @MockitoBean
    private UpdateAccountCommand updateAccountCommand;

    @MockitoBean
    private ReplacePasswordCommand replacePasswordCommand;

    @MockitoBean
    private ReadAccountsQuery readAccountsQuery;

    @Test
    void refusesAnAnonymousAccountList() throws Exception {
        mockMvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void refusesAccountCreationByAnAuditor() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"agent1\",\"password\":\"Signalements-dev-2026\",\"role\":\"AGENT\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }
    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void forwardsSearchAndFiltersToThePagedQuery() throws Exception {
        mockMvc.perform(get("/api/accounts")
                        .param("page", "2").param("size", "20")
                        .param("search", "agent").param("role", "AGENT").param("enabled", "false"))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(readAccountsQuery).execute(
                2, 20, "agent", cd.gouv.finances.signalements.access.Role.AGENT, false);
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void refusesAccountChangesByAnAuditor() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/accounts/00000000-0000-0000-0000-000000000001")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(updateAccountCommand);
    }
}
