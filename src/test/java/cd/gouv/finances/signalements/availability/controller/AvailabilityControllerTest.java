package cd.gouv.finances.signalements.availability.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cd.gouv.finances.signalements.availability.dto.AvailabilityResponse;
import cd.gouv.finances.signalements.availability.query.ReadAvailabilityQuery;
import cd.gouv.finances.signalements.config.PlatformProperties;
import cd.gouv.finances.signalements.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AvailabilityController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(PlatformProperties.class)
@TestPropertySource(properties = {
        "app.frontend-origin=http://localhost:3000",
        "app.documentation-open=true"
})
class AvailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReadAvailabilityQuery readAvailabilityQuery;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void exposesAvailabilityWithoutAuthentication() throws Exception {
        when(readAvailabilityQuery.execute()).thenReturn(new AvailabilityResponse(true));

        mockMvc.perform(get("/api/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void opensDocumentationWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/scalar"))
                .andExpect(status().isNotFound());
    }
}
