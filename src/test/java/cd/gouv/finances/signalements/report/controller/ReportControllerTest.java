package cd.gouv.finances.signalements.report.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cd.gouv.finances.signalements.config.PlatformProperties;
import cd.gouv.finances.signalements.config.SecurityConfig;
import cd.gouv.finances.signalements.report.command.AssignReportCommand;
import cd.gouv.finances.signalements.audit.command.RecordAudit;
import cd.gouv.finances.signalements.report.command.DecideDuplicateCommand;
import cd.gouv.finances.signalements.report.command.LinkPhotoRequestCommand;
import cd.gouv.finances.signalements.report.duplicate.PrepareDuplicateAlerts;
import cd.gouv.finances.signalements.report.command.RecordQualificationCommand;
import cd.gouv.finances.signalements.report.command.RequestPhotoCommand;
import cd.gouv.finances.signalements.report.command.RequestPhotoReadingCommand;
import cd.gouv.finances.signalements.report.command.RequestImportCommand;
import cd.gouv.finances.signalements.report.query.ReadAssigneesQuery;
import cd.gouv.finances.signalements.report.query.ReadImportsQuery;
import cd.gouv.finances.signalements.report.query.ReadReportPhotoQuery;
import cd.gouv.finances.signalements.report.query.ReadReportQuery;
import cd.gouv.finances.signalements.report.query.ReadReportsQuery;
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

@WebMvcTest({ReportController.class, ReportImportController.class})
@Import(SecurityConfig.class)
@EnableConfigurationProperties(PlatformProperties.class)
@TestPropertySource(properties = {
        "app.frontend-origin=http://localhost:3000",
        "app.documentation-open=false"
})
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private cd.gouv.finances.signalements.report.repository.ReportImportRepository reportImportRepository;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private ReadReportsQuery readReportsQuery;

    @MockitoBean
    private ReadReportQuery readReportQuery;

    @MockitoBean
    private ReadReportPhotoQuery readReportPhotoQuery;

    @MockitoBean
    private RequestImportCommand requestImportCommand;

    @MockitoBean
    private ReadImportsQuery readImportsQuery;

    @MockitoBean
    private ReadAssigneesQuery readAssigneesQuery;

    @MockitoBean
    private AssignReportCommand assignReportCommand;

    @MockitoBean
    private RequestPhotoReadingCommand requestPhotoReadingCommand;

    @MockitoBean
    private RequestPhotoCommand requestPhotoCommand;

    @MockitoBean
    private LinkPhotoRequestCommand linkPhotoRequestCommand;

    @MockitoBean
    private PrepareDuplicateAlerts prepareDuplicateAlerts;

    @MockitoBean
    private DecideDuplicateCommand decideDuplicateCommand;

    @MockitoBean
    private RecordQualificationCommand recordQualificationCommand;

    @MockitoBean
    private RecordAudit recordAudit;

    @Test
    void refusesAnAnonymousReportList() throws Exception {
        mockMvc.perform(get("/api/reports")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void refusesAnImportByAnAuditor() throws Exception {
        mockMvc.perform(post("/api/report-imports")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"start\":\"2026-01-01\",\"end\":\"2026-11-01\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void refusesAPhotoRequestByAnAuditor() throws Exception {
        mockMvc.perform(post("/api/reports/" + java.util.UUID.randomUUID() + "/photos/" + java.util.UUID.randomUUID() + "/requests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Photo floue\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void refusesAQualificationByAnAuditor() throws Exception {
        mockMvc.perform(post("/api/reports/" + java.util.UUID.randomUUID() + "/qualifications")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"reject\",\"justification\":\"Pas de manquement visible.\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void aReadingReusesTheCacheUnlessRefreshIsExplicit() throws Exception {
        var reportId = java.util.UUID.randomUUID();
        var photoId = java.util.UUID.randomUUID();
        var path = "/api/reports/" + reportId + "/photos/" + photoId + "/readings";

        mockMvc.perform(post(path).with(csrf())).andExpect(status().isOk());
        org.mockito.Mockito.verify(requestPhotoReadingCommand).execute(reportId, photoId, false);
        mockMvc.perform(post(path).param("refresh", "true").with(csrf())).andExpect(status().isOk());
        org.mockito.Mockito.verify(requestPhotoReadingCommand).execute(reportId, photoId, true);
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void anAuditorCannotTriggerAnOcrCall() throws Exception {
        mockMvc.perform(post("/api/reports/" + java.util.UUID.randomUUID() + "/photos/" + java.util.UUID.randomUUID() + "/readings")
                        .param("refresh", "true").with(csrf()))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(requestPhotoReadingCommand);
    }
}
