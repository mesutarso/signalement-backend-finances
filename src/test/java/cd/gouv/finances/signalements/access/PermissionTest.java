package cd.gouv.finances.signalements.access;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PermissionTest {
    @Test void administratorInheritsEveryPermissionIncludingPaymentStages() {
        assertThat(Permission.forRole(Role.ADMINISTRATOR)).containsExactlyElementsOf(Arrays.stream(Permission.values()).map(Permission::code).toList());
    }
    @Test void auditorHasOnlyReadPermissionsAndNoReporterIdentity() {
        assertThat(Permission.forRole(Role.AUDITOR)).allMatch(p -> p.endsWith(".read"));
        assertThat(Permission.REPORT_IDENTITY.grantedTo(Role.AUDITOR)).isFalse();
    }
    @Test void paymentStagesBelongToDifferentBusinessRoles() {
        assertThat(Permission.forRole(Role.CELLULE)).contains("payment.visa").doesNotContain("payment.authorize", "payment.execute");
        assertThat(Permission.forRole(Role.CABINET)).contains("payment.authorize").doesNotContain("payment.visa", "payment.execute");
        assertThat(Permission.forRole(Role.PAYMASTER)).contains("payment.execute").doesNotContain("payment.visa", "payment.authorize");
    }
    @Test void externalDgiServicesCannotReadReporterIdentityOrAdministerUsers() {
        assertThat(Permission.forRole(Role.DGI_OPERATIONS)).contains("letter.read", "letter.response").doesNotContain("report.identity", "user.update", "payment.execute");
        assertThat(Permission.forRole(Role.DGI)).contains("report.review").doesNotContain("report.identity");
    }
}
