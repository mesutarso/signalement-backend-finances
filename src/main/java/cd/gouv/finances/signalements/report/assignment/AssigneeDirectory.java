package cd.gouv.finances.signalements.report.assignment;

import cd.gouv.finances.signalements.access.Role;
import cd.gouv.finances.signalements.access.repository.UserAccountRepository;
import cd.gouv.finances.signalements.report.dto.AssigneeResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AssigneeDirectory {

    private final UserAccountRepository userAccountRepository;

    public boolean isAssignableAgent(UUID id) {
        return userAccountRepository.findById(id)
                .filter(account -> account.isEnabled() && account.getRole() == Role.AGENT)
                .isPresent();
    }

    public Map<UUID, String> names(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userAccountRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(account -> account.getId(), account -> account.getUsername()));
    }

    public List<AssigneeResponse> agents() {
        return userAccountRepository.findByRoleAndEnabledOrderByUsernameAsc(Role.AGENT, true).stream()
                .map(account -> new AssigneeResponse(account.getId(), account.getUsername()))
                .toList();
    }

    public static String name(Map<UUID, String> names, UUID id) {
        if (cd.gouv.finances.signalements.report.validation.ValidationRules.AUTOMATION.equals(id)) return "Traitement automatique";
        if (id == null) {
            return null;
        }
        return names.getOrDefault(id, "Compte introuvable");
    }
}
