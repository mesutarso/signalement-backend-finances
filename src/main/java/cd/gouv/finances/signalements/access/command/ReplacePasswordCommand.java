package cd.gouv.finances.signalements.access.command;

import cd.gouv.finances.signalements.access.AccessRejected;
import cd.gouv.finances.signalements.access.PasswordPolicy;
import cd.gouv.finances.signalements.access.Role;
import cd.gouv.finances.signalements.access.dto.ReplacePasswordRequest;
import cd.gouv.finances.signalements.access.repository.UserAccountRepository;
import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReplacePasswordCommand {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void execute(UUID accountId, ReplacePasswordRequest request, AccountUserDetails caller) {
        boolean administrator = caller.getRole() == Role.ADMINISTRATOR;
        boolean self = caller.getAccountId().equals(accountId);
        if (!self && !administrator) {
            throw new AccessRejected("forbidden", 403);
        }
        var account = userAccountRepository.findById(accountId).orElseThrow(() -> new AccessRejected("absent", 404));
        if (self && (request.currentPassword() == null
                || !passwordEncoder.matches(request.currentPassword(), account.getPasswordHash()))) {
            throw new AccessRejected("rejected-credentials", 401);
        }
        if (!PasswordPolicy.accepts(request.password(), account.getUsername())) {
            throw new AccessRejected("validation", 400);
        }
        account.changePassword(passwordEncoder.encode(request.password()));
    }
}
