package cd.gouv.finances.signalements.access.command;

import cd.gouv.finances.signalements.access.AccessRejected;
import cd.gouv.finances.signalements.access.Role;
import cd.gouv.finances.signalements.access.dto.AccountResponse;
import cd.gouv.finances.signalements.access.dto.UpdateAccountRequest;
import cd.gouv.finances.signalements.access.mapper.AccountMapper;
import cd.gouv.finances.signalements.access.repository.UserAccountRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateAccountCommand {

    private final UserAccountRepository userAccountRepository;

    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Transactional
    public AccountResponse execute(UUID accountId, UpdateAccountRequest request) {
        if (request.role() == null && request.enabled() == null) {
            throw new AccessRejected("validation", 400);
        }
        userAccountRepository.lockAdministrators();
        var account = userAccountRepository.findById(accountId).orElseThrow(() -> new AccessRejected("absent", 404));
        Role nextRole = request.role() == null ? account.getRole() : request.role();
        boolean nextEnabled = request.enabled() == null ? account.isEnabled() : request.enabled();
        boolean losesAdministration = account.getRole() == Role.ADMINISTRATOR
                && account.isEnabled()
                && (nextRole != Role.ADMINISTRATOR || !nextEnabled);
        if (losesAdministration && userAccountRepository.countByRoleAndEnabled(Role.ADMINISTRATOR, true) <= 1) {
            throw new AccessRejected("last-administrator", 409);
        }
        account.changeRole(nextRole);
        account.setEnabled(nextEnabled);
        return AccountMapper.toResponse(account);
    }
}
