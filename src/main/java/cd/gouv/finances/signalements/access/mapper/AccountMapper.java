package cd.gouv.finances.signalements.access.mapper;

import cd.gouv.finances.signalements.access.dto.AccountResponse;
import cd.gouv.finances.signalements.access.entity.UserAccount;
import cd.gouv.finances.signalements.access.security.AccountUserDetails;

public final class AccountMapper {

    private AccountMapper() {
    }

    public static AccountResponse toResponse(UserAccount account) {
        return new AccountResponse(
                account.getId(), account.getUsername(), account.getRole().name(), account.isEnabled(),
                cd.gouv.finances.signalements.access.Permission.forRole(account.getRole()), account.getLockedUntil(), account.getCreatedAt());
    }

    public static AccountResponse toResponse(AccountUserDetails details) {
        return new AccountResponse(details.getAccountId(), details.getUsername(), details.getRole().name(), details.isEnabled());
    }
}
