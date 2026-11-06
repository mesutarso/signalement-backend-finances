package cd.gouv.finances.signalements.access.security;

import cd.gouv.finances.signalements.access.Role;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AccountUserDetails implements UserDetails {

    private final UUID accountId;
    private final String username;
    private final String passwordHash;
    private final Role role;
    private final boolean enabled;
    private final boolean nonLocked;

    public AccountUserDetails(
            UUID accountId,
            String username,
            String passwordHash,
            Role role,
            boolean enabled,
            boolean nonLocked) {
        this.accountId = accountId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
        this.nonLocked = nonLocked;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return nonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
