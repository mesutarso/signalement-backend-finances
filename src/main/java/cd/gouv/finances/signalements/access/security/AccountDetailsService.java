package cd.gouv.finances.signalements.access.security;

import cd.gouv.finances.signalements.access.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;
    private final Clock clock;

    @Override
    public UserDetails loadUserByUsername(String username) {
        var account = userAccountRepository
                .findByUsername(normalize(username))
                .orElseThrow(() -> new UsernameNotFoundException("rejected-credentials"));
        Instant now = clock.instant();
        boolean locked = account.getLockedUntil() != null && account.getLockedUntil().isAfter(now);
        return new AccountUserDetails(
                account.getId(),
                account.getUsername(),
                account.getPasswordHash(),
                account.getRole(),
                account.isEnabled(),
                !locked);
    }

    public static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
