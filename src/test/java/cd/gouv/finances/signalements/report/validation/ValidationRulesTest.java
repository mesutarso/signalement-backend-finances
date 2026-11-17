package cd.gouv.finances.signalements.report.validation;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ValidationRulesTest {
    @ParameterizedTest
    @CsvSource({
        "registered,non-normalized,unknown,unknown,validated,non-normalized",
        "unregistered,unknown,unknown,yes,validated,vat-unregistered"
    })
    void acceptsExactlyTheApprovedAlternativeCases(String vat, String normalization, String purchase, String charged, String state, String finding) {
        var outcome = ValidationRules.assess(facts(vat, normalization, purchase, charged, "unique"));
        assertThat(outcome.state()).isEqualTo(state);
        assertThat(outcome.finding()).isEqualTo(finding);
    }

    @ParameterizedTest
    @CsvSource({
        "unknown,non-normalized,nonconform,yes,unique,doubt",
        "registered,normalized,unknown,yes,unique,rejected",
        "registered,normalized,nonconform,yes,unique,rejected",
        "registered,unknown,nonconform,yes,unique,doubt",
        "registered,unknown,conform,yes,unique,doubt",
        "unregistered,non-normalized,nonconform,unknown,unique,doubt",
        "registered,non-normalized,nonconform,yes,unknown,doubt",
        "registered,normalized,conform,yes,unique,rejected",
        "unregistered,non-normalized,nonconform,no,unique,rejected",
        "registered,non-normalized,nonconform,yes,duplicate,rejected"
    })
    void unknownEvidenceIsNotAProofAndARegisteredSellerAloneIsNotAnAnomaly(String vat, String normalization, String purchase, String charged, String unique, String expected) {
        assertThat(ValidationRules.assess(facts(vat, normalization, purchase, charged, unique)).state()).isEqualTo(expected);
    }

    @Test
    void requiresAnIdentifiableSellerAnAttachmentAndResolvedDuplicateAlerts() {
        assertThat(ValidationRules.assess(new ValidationRules.Facts("registered", "non-normalized", "unknown", "yes", "unique", false, true, false)).state()).isEqualTo("doubt");
        assertThat(ValidationRules.assess(new ValidationRules.Facts("registered", "non-normalized", "unknown", "yes", "unique", true, false, false)).state()).isEqualTo("doubt");
        assertThat(ValidationRules.assess(new ValidationRules.Facts("registered", "non-normalized", "unknown", "yes", "unique", true, true, true)).state()).isEqualTo("doubt");
    }

    @Test
    void refusesToGuessTaxFromUnreadableOrAmbiguousAmounts() {
        assertThat(AssessmentEvidence.charged(null)).isEqualTo("unknown");
        assertThat(AssessmentEvidence.charged("16,000.00 CDF")).isEqualTo("unknown");
        assertThat(AssessmentEvidence.charged("0")).isEqualTo("no");
        assertThat(AssessmentEvidence.charged("16000,00")).isEqualTo("yes");
        assertThat(new UnavailableFiscalRegistry().lookup("SYNTHETIC-NIF", "Vendeur de recette").status()).isEqualTo("unavailable");
    }

    private static ValidationRules.Facts facts(String vat, String normalization, String purchase, String charged, String uniqueness) {
        return new ValidationRules.Facts(vat, normalization, purchase, charged, uniqueness, true, true, false);
    }

    @Test
    void screensMissingTaxIdOnlyAfterASuccessfulReadingAndBeforeFiscalCriteria() {
        var missing = new ValidationRules.Facts("unknown", "unknown", "unknown", "unknown", "unknown", true, false, false);
        for (var quality : new String[] {"READABLE", "PARTIAL"}) {
            assertThat(ValidationRules.assess(missing, false, quality).state()).isEqualTo("rejected");
            assertThat(ValidationRules.screeningReason(missing, quality)).isEqualTo("identity-missing");
        }
        assertThat(ValidationRules.assess(missing, false, "UNKNOWN").state()).isEqualTo("doubt");
        assertThat(ValidationRules.screeningReason(missing, "UNKNOWN")).isNull();
        assertThat(ValidationRules.screeningReason(missing, "UNREADABLE")).isEqualTo("unreadable");
        var duplicate = new ValidationRules.Facts("unknown", "unknown", "unknown", "unknown", "duplicate", true, true, false);
        assertThat(ValidationRules.screeningReason(duplicate, "READABLE")).isEqualTo("duplicate");
    }

    @Test
    void purchaseConformityDoesNotAffectTheDecision() {
        for (var purchase : new String[] {"unknown", "conform", "nonconform"}) {
            assertThat(ValidationRules.assess(facts("registered", "normalized", purchase, "yes", "unique")).state())
                    .isEqualTo("rejected");
            assertThat(ValidationRules.assess(facts("registered", "non-normalized", purchase, "yes", "unique")).state())
                    .isEqualTo("validated");
        }
    }
}
