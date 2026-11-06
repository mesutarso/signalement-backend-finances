package cd.gouv.finances.signalements.report.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import cd.gouv.finances.signalements.report.ReportProperties;
import cd.gouv.finances.signalements.report.ReportRejected;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class FacnoReportSourceTest {

    private static final String PAGE = """
            {
              "totalCount": 1,
              "pageSize": 10,
              "currentPage": 1,
              "totalPages": 1,
              "hasNext": false,
              "hasPrevious": false,
              "problems": [
                {
                  "uid": "signalement-001",
                  "title": "Facture non délivrée",
                  "probDesc": "Le commerçant n'a pas remis de facture.",
                  "reportedDateTime": "2026-10-19T10:30:00",
                  "invoiceProblemImages": [ { "uid": "image-001" } ],
                  "name": "Claire Mbuyi",
                  "email": "claire.mbuyi@exemple.cd",
                  "phone": "+243810000000",
                  "uidUser": "utilisateur-001"
                }
              ]
            }
            """;

    private MockRestServiceServer server;
    private FacnoReportSource source;
    private boolean stored;

    @BeforeEach
    void setUp() {
        stored = false;
        var builder = RestClient.builder().baseUrl("https://facno.test");
        server = MockRestServiceServer.bindTo(builder).build();
        StoredPhotoIndex index = (reportExternalId, photoExternalId) -> stored;
        source = new FacnoReportSource(builder.build(), new ReportProperties(
                false,
                new ReportProperties.Facno("https://facno.test", "test-key", Duration.ofSeconds(5))), index);
    }

    @Test
    void readsOneProblemAndDownloadsItsImage() {
        server.expect(requestTo(containsString("/GetProblems")))
                .andExpect(header("x-api-key", "test-key"))
                .andRespond(withSuccess(PAGE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/GetImage/image-001")))
                .andRespond(withSuccess(new byte[] {1, 2, 3}, MediaType.IMAGE_PNG));

        var page = source.read(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-11-01"), 1, 10);

        assertThat(page.last()).isTrue();
        assertThat(page.reports()).singleElement().satisfies(report -> {
            assertThat(report.externalId()).isEqualTo("signalement-001");
            assertThat(report.title()).isEqualTo("Facture non délivrée");
            assertThat(report.reporterEmail()).isEqualTo("claire.mbuyi@exemple.cd");
            assertThat(report.photos()).singleElement().satisfies(photo -> {
                assertThat(photo.externalId()).isEqualTo("image-001");
                assertThat(photo.contentType()).isEqualTo("image/png");
                assertThat(photo.content()).containsExactly(1, 2, 3);
            });
        });
        server.verify();
    }

    @Test
    void keepsAPhotoAlreadyStored() {
        stored = true;
        server.expect(requestTo(containsString("/GetProblems")))
                .andRespond(withSuccess(PAGE, MediaType.APPLICATION_JSON));

        var page = source.read(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-11-01"), 1, 10);

        assertThat(page.reports()).singleElement().satisfies(report ->
                assertThat(report.photos()).singleElement().satisfies(photo -> {
                    assertThat(photo.alreadyStored()).isTrue();
                    assertThat(photo.content()).isEmpty();
                }));
        server.verify();
    }

    @Test
    void rejectsAnUnavailableSource() {
        server.expect(requestTo(containsString("/GetProblems")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> source.read(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-11-01"), 1, 10))
                .isInstanceOf(ReportRejected.class);
    }
    @Test
    void opensTheCircuitAfterRepeatedFailuresWithoutCallingTheApiAgain() {
        server.expect(org.springframework.test.web.client.ExpectedCount.times(5), requestTo(containsString("/GetProblems")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> source.read(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-06"), 1, 10))
                    .isInstanceOf(ReportRejected.class).hasMessage("source");
        }
        assertThatThrownBy(() -> source.read(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-06"), 1, 10))
                .isInstanceOf(ReportRejected.class).hasMessage("circuit-open");
        server.verify();
    }
}
