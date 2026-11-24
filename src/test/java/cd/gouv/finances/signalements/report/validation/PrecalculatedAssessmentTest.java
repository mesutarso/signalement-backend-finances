package cd.gouv.finances.signalements.report.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PrecalculatedAssessmentTest {

    @Test
    void bothFiscalOutcomesStillRequireAnAgentDecision() {
        var facts = new ValidationRules.Facts("unknown", "non-normalized", "no", "unique", true, true, false);
        var result = PrecalculatedAssessment.calculate(snapshot(facts, "done"), facts, false, "READABLE", false);

        assertThat(result.fiscalStatusMissing()).isTrue();
        assertThat(result.ifRegistered().state()).isEqualTo("doubt");
        assertThat(result.ifUnregistered().state()).isEqualTo("doubt");
    }

    @ParameterizedTest
    @ValueSource(strings = {"yes", "no", "unknown"})
    void displaysNormalizationMarkersAndSkipsFiscalVerification(String vatCharged) {
        var facts = new ValidationRules.Facts("unknown", "unknown", vatCharged, "unique", true, true, false);
        var result = PrecalculatedAssessment.calculate(snapshot(facts, "done"), facts, false, "READABLE", true);

        assertThat(result.fiscalReviewRequired()).isFalse();
        assertThat(result.fiscalStatusMissing()).isFalse();
        assertThat(result.criteria()).filteredOn(criterion -> criterion.key().equals("normalization"))
                .singleElement().satisfies(criterion -> assertThat(criterion.value()).isEqualTo("markers-found"));
        assertThat(result.ifRegistered().state()).isEqualTo("doubt");
    }

    @Test
    void neverPromisesAConclusionWhileAnalysisIsQueued() {
        var facts = new ValidationRules.Facts("unknown", "unknown", "yes", "unique", true, true, false);
        var result = PrecalculatedAssessment.calculate(snapshot(facts, "queued"), facts, false, "READABLE", false);

        assertThat(result.analysisComplete()).isFalse();
        assertThat(result.ifRegistered().state()).isEqualTo("doubt");
        assertThat(result.ifUnregistered().state()).isEqualTo("doubt");
    }

    private static AssessmentStore.Snapshot snapshot(ValidationRules.Facts facts, String jobState) {
        return new AssessmentStore.Snapshot(UUID.randomUUID(), 1, "doubt", jobState, "Recette", UUID.randomUUID(),
                "Vendeur fictif", "DEMO-NIF", "DEMO-FACTURE", LocalDate.of(2026, 11, 15),
                facts.vatStatus(), null, null, null, null, facts.normalization(), null, "unknown", null,
                facts.vatCharged(), "TVA sur la pièce", facts.uniqueness(), "Recherche de recette",
                Map.of(), List.of(), Instant.parse("2026-11-15T12:00:00Z"));
    }
}
