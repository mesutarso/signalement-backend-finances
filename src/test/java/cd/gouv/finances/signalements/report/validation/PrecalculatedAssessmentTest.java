package cd.gouv.finances.signalements.report.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PrecalculatedAssessmentTest {
    @Test
    void preparesBothDecisionsWithoutFiscalAccessOrInventingTheFiscalStatus() {
        var facts = facts("unknown", "non-normalized", "unknown", "no", "unique", false);
        var result = PrecalculatedAssessment.calculate(snapshot(facts, "done", Map.of()), facts, false, "READABLE");

        assertThat(result.ifRegistered().state()).isEqualTo("validated");
        assertThat(result.ifUnregistered().state()).isEqualTo("rejected");
        assertThat(result.onlyFiscalStatusMissing()).isTrue();
        assertThat(ValidationRules.assess(facts).state()).isEqualTo("doubt");
    }

    @Test
    void identifiesWhichFiscalBranchStillNeedsNormalizationEvidence() {
        var facts = facts("unknown", "unknown", "unknown", "yes", "unique", false);
        var result = PrecalculatedAssessment.calculate(snapshot(facts, "done", Map.of()), facts, false, "READABLE");

        assertThat(result.ifRegistered().state()).isEqualTo("doubt");
        assertThat(result.ifUnregistered().state()).isEqualTo("validated");
        assertThat(result.onlyFiscalStatusMissing()).isFalse();
        assertThat(result.criteria()).noneMatch(c -> c.key().equals("purchaseConformity"));
        assertThat(result.criteria()).filteredOn(c -> c.key().equals("normalization"))
                .singleElement().satisfies(c -> assertThat(c.value()).isEqualTo("unknown"));
    }

    @Test
    void defReferencesAreCluesAndDoNotTurnIntoVerifiedNormalization() {
        var facts = facts("unknown", "unknown", "unknown", "yes", "unique", false);
        var result = PrecalculatedAssessment.calculate(snapshot(facts, "done", Map.of("defCode", "DEMO")), facts, false, "READABLE");
        assertThat(result.criteria()).filteredOn(c -> c.key().equals("normalization")).singleElement().satisfies(c -> {
            assertThat(c.value()).isEqualTo("unknown");
            assertThat(c.detail()).contains("Références DEF repérées");
        });
    }

    @Test
    void pendingDuplicateAndRewardCollisionAlsoHoldTheFiscalForecasts() {
        var facts = facts("unknown", "non-normalized", "unknown", "yes", "unique", true);
        var pending = PrecalculatedAssessment.calculate(snapshot(facts, "done", Map.of()), facts, false, "READABLE");
        assertThat(pending.ifRegistered().state()).isEqualTo("doubt");
        assertThat(pending.ifUnregistered().state()).isEqualTo("doubt");
        var clear = facts("unknown", "non-normalized", "unknown", "yes", "unique", false);
        var collision = PrecalculatedAssessment.calculate(snapshot(clear, "done", Map.of()), clear, true, "READABLE");
        assertThat(collision.ifRegistered().state()).isEqualTo("doubt");
        assertThat(collision.ifUnregistered().state()).isEqualTo("doubt");
        assertThat(collision.onlyFiscalStatusMissing()).isFalse();
    }

    @Test
    void unreadablePhotoWaitsForReplacementInBothBranches() {
        var facts = facts("unknown", "unknown", "unknown", "unknown", "unknown", false);
        var result = PrecalculatedAssessment.calculate(snapshot(facts, "done", Map.of()), facts, false, "UNREADABLE");
        assertThat(result.ifRegistered().state()).isEqualTo("doubt");
        assertThat(result.ifUnregistered().state()).isEqualTo("doubt");
        assertThat(result.missingFields()).contains("Photo lisible");
        assertThat(result.onlyFiscalStatusMissing()).isFalse();
        assertThat(ValidationRules.assess(facts, false, "UNREADABLE").reason()).isEqualTo("Une photo plus nette de la facture est nécessaire.");
        assertThat(ValidationRules.assess(facts, false, "UNKNOWN").state()).isEqualTo("doubt");
    }

    @Test
    void neverPromisesAConclusionWhileAnalysisIsQueued() {
        var facts = facts("unknown", "non-normalized", "unknown", "yes", "unique", false);
        var result = PrecalculatedAssessment.calculate(snapshot(facts, "queued", Map.of()), facts, false, "READABLE");
        assertThat(result.analysisComplete()).isFalse();
        assertThat(result.ifRegistered().state()).isEqualTo("doubt");
        assertThat(result.ifUnregistered().state()).isEqualTo("doubt");
    }

    private static ValidationRules.Facts facts(String vat, String normalization, String purchase, String charged, String unique, boolean duplicatePending) {
        return new ValidationRules.Facts(vat, normalization, purchase, charged, unique, true, true, duplicatePending);
    }

    private static AssessmentStore.Snapshot snapshot(ValidationRules.Facts facts, String jobState, Map<String, String> fields) {
        return new AssessmentStore.Snapshot(UUID.randomUUID(), 1, "doubt", jobState, "Recette", UUID.randomUUID(),
                "Vendeur fictif", "DEMO-NIF", "DEMO-FACTURE", LocalDate.of(2026, 11, 15),
                facts.vatStatus(), null, null, null, null, facts.normalization(), "Justificatif de recette",
                facts.purchaseConformity(), null, facts.vatCharged(), "TVA sur la pièce", facts.uniqueness(),
                "Recherche de recette", fields, List.of(), Instant.parse("2026-11-15T12:00:00Z"));
    }
}
