package cd.gouv.finances.signalements.access.security;

import cd.gouv.finances.signalements.audit.command.RecordAudit;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginAttemptListener {

    private final LoginAttemptTracker loginAttemptTracker;
    private final RecordAudit recordAudit;

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        loginAttemptTracker.recordFailure(event.getAuthentication().getName());
        recordAudit.refused(null, "login", "session", null);
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        loginAttemptTracker.recordSuccess(event.getAuthentication().getName());
        if (event.getAuthentication().getPrincipal() instanceof AccountUserDetails account) {
            recordAudit.allowed(account.getAccountId(), "login", "session", account.getAccountId().toString());
        }
    }
}
