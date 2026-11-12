package cd.gouv.finances.signalements.araka;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ArakaResponseTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private ArakaResult parse(int http, String json, boolean inquiry) {
        return ArakaResponse.parse(http, mapper.readTree(json), "FC1", null, inquiry);
    }

    @Test void acceptsBothObservedStatusFormatsAndReferenceArrays() {
        assertThat(parse(200, """
                {"statusCode":"200","statusDescription":"APPROVED","transactionId":"T1","originatingTransactionId":"FC1"}
                """, false).state()).isEqualTo("paid");
        assertThat(parse(200, """
                [{"statusCode":200,"status":"APPROVED","transactionId":"T1","transactionReference":"FC1"}]
                """, true).state()).isEqualTo("paid");
    }

    @Test void doesNotTreatHttpSuccessOrAcceptedAsProofOfPayment() {
        assertThat(parse(200, "{}", false).state()).isEqualTo("unknown");
        assertThat(parse(202, "{\"statusCode\":\"202\",\"statusDescription\":\"ACCEPTED\"}", false).state()).isEqualTo("pending");
        assertThat(parse(200, "{\"statusCode\":\"200\",\"statusDescription\":\"SUCCESS\"}", false).state()).isEqualTo("unknown");
        assertThat(parse(200, "{\"statusCode\":\"200\",\"status\":\"PENDING\"}", true).state()).isEqualTo("unknown");
    }

    @Test void rejectsUnrelatedAndAmbiguousTransactionReferences() {
        assertThat(parse(200, """
                {"statusCode":"200","status":"APPROVED","transactionId":"T1","transactionReference":"OTHER"}
                """, true).state()).isEqualTo("unknown");
        var duplicate = mapper.readTree("""
                [{"statusCode":"200","status":"APPROVED","transactionId":"T1","transactionReference":"FC1"},
                 {"statusCode":"200","status":"APPROVED","transactionId":"T2","transactionReference":"FC1"}]
                """);
        assertThat(ArakaResponse.parse(200, duplicate, "FC1", "T1", true).code()).isEqualTo("ambiguous-reference");
    }

    @Test void onlyAnExplicitBusinessRejectionIsFinalAndNotFoundStaysUncertain() {
        assertThat(parse(400, "{\"statusCode\":400,\"statusDescription\":\"FAILED\"}", false).state()).isEqualTo("failed");
        assertThat(parse(400, "{\"statusCode\":400,\"statusDescription\":\"Transaction Failed\",\"originatingTransactionId\":\"FC1\"}", false).state()).isEqualTo("failed");
        assertThat(parse(400, "{}", false).state()).isEqualTo("unknown");
        assertThat(parse(404, "{}", true).state()).isEqualTo("unknown");
        assertThat(parse(500, "{}", false).providerFailure()).isTrue();
    }
}
