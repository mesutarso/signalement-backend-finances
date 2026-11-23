package cd.gouv.finances.signalements.report.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ValidationRulesTest {

    @Test
    void automaticallyValidatesReadableInvoicesWithVatAndWithoutNormalizationMarkers() {
        var facts = new ValidationRules.Facts("unknown", "unknown", "yes", "unique", true, false, false);

        var outcome = ValidationRules.assess(facts, false, "READABLE", false);

        assertThat(outcome.state()).isEqualTo("validated");
        assertThat(outcome.finding()).isEqualTo("vat-without-normalization");
        assertThat(ValidationRules.screeningReason(facts, "READABLE")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"defCode", "defNumber", "readCode"})
    void leavesAnyNormalizationMarkerForAnAgentToCheck(String field) {
        var facts = facts("registered", "yes", "unique", false);
        var markers = AssessmentEvidence.hasNormalizationMarkers(Map.of(field, "DEMO-123"));

        assertThat(ValidationRules.assess(facts, false, "READABLE", markers).state()).isEqualTo("doubt");
    }

    @ParameterizedTest
    @CsvSource({"unknown,no", "unknown,unknown", "registered,no", "unregistered,no"})
    void leavesEveryInvoiceWithoutDetectedVatForTheAgentToDecide(String status, String charged) {
        var facts = facts(status, charged, "unique", false);
        assertThat(ValidationRules.assess(facts, false, "READABLE", false).state()).isEqualTo("doubt");
        assertThat(ValidationRules.assess(facts, false, "READABLE", true).state()).isEqualTo("doubt");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PARTIAL", "UNREADABLE", "UNKNOWN"})
    void requiresAReadableInvoiceBeforeInferringTheAbsenceOfMarkers(String readability) {
        assertThat(ValidationRules.assess(facts("registered", "yes", "unique", false), false, readability, false).state())
                .isEqualTo("doubt");
    }

    @Test
    void retainsTheExistingDuplicateAndRewardProtection() {
        var duplicate = facts("registered", "yes", "duplicate", false);
        assertThat(ValidationRules.assess(duplicate, false, "READABLE", false).reason()).isEqualTo("Facture déjà signalée.");
        assertThat(ValidationRules.screeningReason(duplicate, "READABLE")).isEqualTo("duplicate");
        var open = facts("registered", "yes", "unknown", true);
        assertThat(ValidationRules.assess(open, false, "READABLE", false).state()).isEqualTo("doubt");
        assertThat(ValidationRules.assess(facts("registered", "yes", "unique", false), true, "READABLE", false).state())
                .isEqualTo("doubt");
    }

    @Test
    void refusesToGuessTaxFromAmbiguousAmounts() {
        assertThat(AssessmentEvidence.charged(null)).isEqualTo("unknown");
        assertThat(AssessmentEvidence.charged("16,000.00 CDF")).isEqualTo("unknown");
        assertThat(AssessmentEvidence.charged("0")).isEqualTo("no");
        assertThat(AssessmentEvidence.charged("16000,00")).isEqualTo("yes");
    }

    @Test
    void flagsMissingTaxIdAfterAReadingWhenFiscalVerificationIsNeeded() {
        var missing = new ValidationRules.Facts("unknown", "unknown", "unknown", "unknown", true, false, false);
        assertThat(ValidationRules.screeningReason(missing, "READABLE")).isEqualTo("identity-missing");
        assertThat(ValidationRules.screeningReason(missing, "PARTIAL")).isEqualTo("identity-missing");
        assertThat(ValidationRules.screeningReason(missing, "UNKNOWN")).isNull();
        assertThat(ValidationRules.screeningReason(missing, "UNREADABLE")).isEqualTo("unreadable");
    }

    private static ValidationRules.Facts facts(String status, String charged, String uniqueness, boolean openDuplicates) {
        return new ValidationRules.Facts(status, "unknown", charged, uniqueness, true, true, openDuplicates);
    }
}
