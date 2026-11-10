package cd.gouv.finances.signalements.report.letter;

import cd.gouv.finances.signalements.access.security.AccountUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledWeeklyLetters {
    private final WeeklyLettersCommand command;
    private final UserDetailsService users;
    private final cd.gouv.finances.signalements.report.indicator.CampaignReporting reporting;
    @Value("${app.letters.automation-username:}")
    private String username;

    @Scheduled(cron = "${app.letters.cron:0 0 7 * * MON}", zone = "Africa/Kinshasa")
    public void prepare() {
        asAutomation(user -> command.prepare(user.getAccountId()));
    }

    @Scheduled(cron = "${app.letters.weekly-report-cron:0 5 7 * * MON}", zone = "Africa/Kinshasa")
    public void weeklyReport() {
        asAutomation(user -> reporting.capture("weekly", user.getAccountId()));
    }

    @Scheduled(cron = "${app.letters.monthly-report-cron:0 10 7 1 * *}", zone = "Africa/Kinshasa")
    public void monthlyReport() {
        asAutomation(user -> reporting.capture("monthly", user.getAccountId()));
    }

    private void asAutomation(java.util.function.Consumer<AccountUserDetails> action) {
        if (username.isBlank()) return;
        var previous = SecurityContextHolder.getContext();
        try {
            var user = (AccountUserDetails) users.loadUserByUsername(username);
            if (!user.isEnabled() || !user.isAccountNonLocked()) throw new IllegalStateException("Automation account unavailable");
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
            SecurityContextHolder.setContext(context);
            action.accept(user);
        } catch (RuntimeException exception) {
            log.error("Campaign automation failed: {}", exception.getClass().getSimpleName());
        } finally { SecurityContextHolder.setContext(previous); }
    }
}
