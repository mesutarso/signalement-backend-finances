package cd.gouv.finances.signalements.access.security;

import cd.gouv.finances.signalements.access.repository.UserAccountRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LoginAttemptTracker {

    private final UserAccountRepository userAccountRepository;
    private final Clock clock;

    @Transactional
    public void recordFailure(String username) {
        userAccountRepository.findByUsername(AccountDetailsService.normalize(username)).ifPresent(account -> {
            if (account.getLockedUntil() != null && account.getLockedUntil().isAfter(clock.instant())) {
                return;
            }
            account.recordFailedSignIn(clock.instant());
        });
    }

    @Transactional
    public void recordSuccess(String username) {
        userAccountRepository
                .findByUsername(AccountDetailsService.normalize(username))
                .ifPresent(account -> account.recordSuccessfulSignIn());
    }
}
