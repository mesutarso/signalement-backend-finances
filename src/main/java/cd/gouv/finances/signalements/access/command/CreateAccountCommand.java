package cd.gouv.finances.signalements.access.command;

import cd.gouv.finances.signalements.access.AccessRejected;
import cd.gouv.finances.signalements.access.PasswordPolicy;
import cd.gouv.finances.signalements.access.dto.AccountResponse;
import cd.gouv.finances.signalements.access.dto.CreateAccountRequest;
import cd.gouv.finances.signalements.access.entity.UserAccount;
import cd.gouv.finances.signalements.access.mapper.AccountMapper;
import cd.gouv.finances.signalements.access.repository.UserAccountRepository;
import cd.gouv.finances.signalements.access.security.AccountDetailsService;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateAccountCommand {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Transactional
    public AccountResponse execute(CreateAccountRequest request) {
        var username = AccountDetailsService.normalize(request.username());
        if (!PasswordPolicy.accepts(request.password(), username)) {
            throw new AccessRejected("validation", 400);
        }
        if (userAccountRepository.existsByUsername(username)) {
            throw new AccessRejected("conflict", 409);
        }
        var account = new UserAccount(
                UUID.randomUUID(),
                username,
                passwordEncoder.encode(request.password()),
                request.role(),
                clock.instant());
        return AccountMapper.toResponse(userAccountRepository.save(account));
    }
}
