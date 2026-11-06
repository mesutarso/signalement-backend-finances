package cd.gouv.finances.signalements.access.entity;

import cd.gouv.finances.signalements.access.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "user_account")
@Getter
public class UserAccount {

    @Id
    private UUID id;

    @Column(nullable = false, length = 80, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserAccount() {
    }

    public UserAccount(UUID id, String username, String passwordHash, Role role, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
        this.failedAttempts = 0;
        this.createdAt = createdAt;
    }

    public void recordFailedSignIn(Instant now) {
        failedAttempts++;
        if (failedAttempts >= 5) {
            failedAttempts = 0;
            lockedUntil = now.plus(Duration.ofMinutes(15));
        }
    }

    public void recordSuccessfulSignIn() {
        failedAttempts = 0;
        lockedUntil = null;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void changeRole(Role role) {
        this.role = role;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
