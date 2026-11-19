package cd.gouv.finances.signalements.report.command;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import cd.gouv.finances.signalements.report.ReportRejected;
import cd.gouv.finances.signalements.report.validation.AnalysisJobs;
import cd.gouv.finances.signalements.report.validation.AssessmentStore;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class RequestPhotoCommandTest {
    @Test void refusesPhotoRequestsOnAClosedDossier() {
        var jdbc=mock(JdbcTemplate.class);var jobs=mock(AnalysisJobs.class);var store=mock(AssessmentStore.class);
        var id=UUID.randomUUID();var snapshot=mock(AssessmentStore.Snapshot.class);
        when(store.lock(id)).thenReturn(snapshot);when(snapshot.state()).thenReturn("validated");
        var command=new RequestPhotoCommand(jdbc,jobs,store,Clock.systemUTC());
        assertThatThrownBy(()->command.execute(id,UUID.randomUUID(),"Photo floue",UUID.randomUUID()))
                .isInstanceOf(ReportRejected.class).extracting(e->((ReportRejected)e).code()).isEqualTo("photo-request-state");
        verifyNoInteractions(jdbc);
    }
}
