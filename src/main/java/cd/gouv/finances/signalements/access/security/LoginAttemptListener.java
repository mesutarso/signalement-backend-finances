package cd.gouv.finances.signalements.access.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginAttemptListener {

    private final LoginAttemptTracker loginAttemptTracker;

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        loginAttemptTracker.recordFailure(event.getAuthentication().getName());
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        loginAttemptTracker.recordSuccess(event.getAuthentication().getName());
    }
}
