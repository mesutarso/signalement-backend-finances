package cd.gouv.finances.signalements.report.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AssessmentEvidenceTest {
    @Test
    void technicalOcrFailureDoesNotEstablishThatThePhotoIsUnreadable() {
        var jdbc = mock(JdbcTemplate.class);
        var photo = UUID.randomUUID();
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<AssessmentEvidence.Reading>>any(), eq(photo.toString())))
                .thenReturn(List.of(new AssessmentEvidence.Reading(photo, UUID.randomUUID(), "FAILED", null, "test", "quota", Map.of())));
        assertThat(new AssessmentEvidence(jdbc).readability(UUID.randomUUID(), photo)).isEqualTo("UNKNOWN");
    }

    @Test
    void successfulUnreadableReadingIsAnExplicitRejectionSignal() {
        var jdbc = mock(JdbcTemplate.class);
        var photo = UUID.randomUUID();
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<AssessmentEvidence.Reading>>any(), eq(photo.toString())))
                .thenReturn(List.of(new AssessmentEvidence.Reading(photo, UUID.randomUUID(), "READ", "UNREADABLE", "test", null, Map.of())));
        assertThat(new AssessmentEvidence(jdbc).readability(UUID.randomUUID(), photo)).isEqualTo("UNREADABLE");
    }
}
