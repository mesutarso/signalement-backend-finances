package cd.gouv.finances.signalements.access.query;

import cd.gouv.finances.signalements.access.dto.AccountPageResponse;
import cd.gouv.finances.signalements.access.mapper.AccountMapper;
import cd.gouv.finances.signalements.access.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import cd.gouv.finances.signalements.access.Role;
import cd.gouv.finances.signalements.access.entity.UserAccount;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReadAccountsQuery {

    private final UserAccountRepository userAccountRepository;

    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public AccountPageResponse execute(int page, int size) {
        return execute(page, size, "", null, null);
    }

    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Transactional(readOnly = true)
    public AccountPageResponse execute(int page, int size, String search, Role role, Boolean enabled) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Specification<UserAccount> filters = (root, query, cb) -> cb.conjunction();
        if (search != null && !search.isBlank()) {
            String term = search.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
            filters = filters.and((root, query, cb) -> cb.like(cb.lower(root.get("username")), "%" + term + "%", '!'));
        }
        if (role != null) filters = filters.and((root, query, cb) -> cb.equal(root.get("role"), role));
        if (enabled != null) filters = filters.and((root, query, cb) -> cb.equal(root.get("enabled"), enabled));
        var result = userAccountRepository.findAll(filters, PageRequest.of(safePage, safeSize, Sort.by("username").ascending()));
        var accounts = result.getContent().stream().map(AccountMapper::toResponse).toList();
        return new AccountPageResponse(accounts, safePage, safeSize, result.getTotalElements());
    }
}
