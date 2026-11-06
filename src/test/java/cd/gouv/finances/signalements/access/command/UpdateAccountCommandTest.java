package cd.gouv.finances.signalements.access.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import cd.gouv.finances.signalements.access.AccessRejected;
import cd.gouv.finances.signalements.access.Role;
import cd.gouv.finances.signalements.access.dto.UpdateAccountRequest;
import cd.gouv.finances.signalements.access.entity.UserAccount;
import cd.gouv.finances.signalements.access.repository.UserAccountRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateAccountCommandTest {
    @Mock UserAccountRepository repository;

    @Test
    void preservesTheLastAdministratorWhenDisablingOrChangingRole() {
        var account = new UserAccount(UUID.randomUUID(), "admin", "hash", Role.ADMINISTRATOR, Instant.now());
        when(repository.findById(account.getId())).thenReturn(Optional.of(account));
        when(repository.countByRoleAndEnabled(Role.ADMINISTRATOR, true)).thenReturn(1L);
        var command = new UpdateAccountCommand(repository);
        assertThatThrownBy(() -> command.execute(account.getId(), new UpdateAccountRequest(null, false)))
                .isInstanceOf(AccessRejected.class).hasMessage("last-administrator");
        assertThatThrownBy(() -> command.execute(account.getId(), new UpdateAccountRequest(Role.AGENT, null)))
                .isInstanceOf(AccessRejected.class).hasMessage("last-administrator");
        assertThat(account.isEnabled()).isTrue();
        assertThat(account.getRole()).isEqualTo(Role.ADMINISTRATOR);
    }

    @Test
    void locksAdministratorsBeforeAllowingAnUpdate() {
        var account = new UserAccount(UUID.randomUUID(), "admin", "hash", Role.ADMINISTRATOR, Instant.now());
        when(repository.findById(account.getId())).thenReturn(Optional.of(account));
        when(repository.countByRoleAndEnabled(Role.ADMINISTRATOR, true)).thenReturn(2L);
        new UpdateAccountCommand(repository).execute(account.getId(), new UpdateAccountRequest(Role.AGENT, false));
        assertThat(account.isEnabled()).isFalse();
        assertThat(account.getRole()).isEqualTo(Role.AGENT);
        var order = inOrder(repository);
        order.verify(repository).lockAdministrators();
        order.verify(repository).findById(account.getId());
    }
}
