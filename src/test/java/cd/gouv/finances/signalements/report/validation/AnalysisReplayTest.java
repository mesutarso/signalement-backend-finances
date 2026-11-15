package cd.gouv.finances.signalements.report.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalysisReplayTest {
    private static final Instant CUTOFF = Instant.parse("2026-11-15T12:00:00Z");

    @Test
    void normalAnalysisReusesSuccessfulReadingsAndDoesNotForceProviderCalls() {
        var policy = new AnalysisReplay("");
        assertThat(policy.reusable(reading("READ", Instant.EPOCH))).isTrue();
        assertThat(policy.reusable(reading("FAILED", Instant.EPOCH))).isFalse();
        assertThat(policy.reusable(null)).isFalse();
        assertThat(policy.refresh()).isFalse();
    }

    @Test
    void historicalReplayForcesOldReadingsButReusesSuccessfulPhotosFromTheSameRun() {
        var policy = new AnalysisReplay(CUTOFF.toString());
        assertThat(policy.reusable(reading("READ", CUTOFF.minusSeconds(1)))).isFalse();
        assertThat(policy.reusable(reading("READ", CUTOFF))).isTrue();
        assertThat(policy.reusable(reading("READ", CUTOFF.plusSeconds(5)))).isTrue();
        assertThat(policy.reusable(reading("READ", null))).isFalse();
        assertThat(policy.refresh()).isTrue();
    }

    @Test
    void failedAttemptsAreNeverMistakenForFreshSuccessfulExtractions() {
        var policy = new AnalysisReplay(CUTOFF.toString());
        assertThat(policy.reusable(reading("FAILED", CUTOFF.plusSeconds(5)))).isFalse();
    }

    private static AssessmentEvidence.Reading reading(String status, Instant createdAt) {
        return new AssessmentEvidence.Reading(UUID.randomUUID(), UUID.randomUUID(), status, "READABLE", "test", null, Map.of(), createdAt);
    }
}
