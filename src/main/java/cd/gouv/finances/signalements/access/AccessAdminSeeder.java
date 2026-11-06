package cd.gouv.finances.signalements.access;

import cd.gouv.finances.signalements.access.entity.UserAccount;
import cd.gouv.finances.signalements.access.repository.UserAccountRepository;
import cd.gouv.finances.signalements.access.security.AccountDetailsService;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccessAdminSeeder.class);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final AccessProperties accessProperties;

    @Override
    public void run(ApplicationArguments args) {
        var username = AccountDetailsService.normalize(accessProperties.adminUsername());
        var password = accessProperties.adminPassword();
        if (username.isBlank() || password == null || password.isBlank()) {
            return;
        }
        if (!PasswordPolicy.accepts(password, username)) {
            throw new IllegalStateException("ACCESS_ADMIN_PASSWORD does not meet the password policy");
        }
        if (userAccountRepository.countByRoleAndEnabled(Role.ADMINISTRATOR, true) > 0) {
            return;
        }
        userAccountRepository.save(new UserAccount(
                UUID.randomUUID(), username, passwordEncoder.encode(password), Role.ADMINISTRATOR, clock.instant()));
        log.info("Created the initial administrator account");
    }
}
