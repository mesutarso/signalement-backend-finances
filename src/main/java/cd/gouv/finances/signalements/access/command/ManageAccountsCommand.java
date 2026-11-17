package cd.gouv.finances.signalements.access.command;

import cd.gouv.finances.signalements.access.AccessRejected;
import cd.gouv.finances.signalements.access.Role;
import cd.gouv.finances.signalements.access.repository.UserAccountRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ManageAccountsCommand {
    private final UserAccountRepository accounts;
    private final Clock clock;

    @Transactional
    @PreAuthorize("hasAuthority('user.delete')")
    public void delete(UUID id, UUID actor) {
        accounts.lockAdministrators();
        var account = accounts.findById(id).filter(a -> a.getDeletedAt() == null)
                .orElseThrow(() -> new AccessRejected("absent", 404));
        if (id.equals(actor)) throw new AccessRejected("self-deletion", 409);
        if (account.getRole() == Role.ADMINISTRATOR && account.isEnabled()
                && accounts.countByRoleAndEnabled(Role.ADMINISTRATOR, true) <= 1) {
            throw new AccessRejected("last-administrator", 409);
        }
        account.archive(clock.instant());
    }

    @Transactional
    @PreAuthorize("hasAuthority('user.unlock')")
    public void unlock(UUID id) {
        var account = accounts.findById(id).filter(a -> a.getDeletedAt() == null)
                .orElseThrow(() -> new AccessRejected("absent", 404));
        account.unlock();
    }
}
