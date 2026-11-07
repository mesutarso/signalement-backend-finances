package cd.gouv.finances.signalements.access;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsALongPasswordDifferentFromTheUsername() {
        assertThat(PasswordPolicy.accepts("Unit-test-42safe", "admin")).isTrue();
    }

    @Test
    void rejectsAShortPasswordAndTheUsernameItself() {
        assertThat(PasswordPolicy.accepts("short1", "admin")).isFalse();
        assertThat(PasswordPolicy.accepts("Administrator1", "administrator1")).isFalse();
    }
}
